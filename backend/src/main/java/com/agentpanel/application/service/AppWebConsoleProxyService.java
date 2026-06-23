package com.agentpanel.application.service;

import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.config.AgentUpstreamHttpClients;
import com.agentpanel.system.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppWebConsoleProxyService {

    private static final int MAX_PROXY_BODY_BYTES = 10 * 1024 * 1024;

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length",
            "content-encoding"
    );
    private static final Pattern LOCAL_ORIGIN = Pattern.compile("https?://(?:127\\.0\\.0\\.1|localhost)(?::\\d+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCAL_WS_ORIGIN = Pattern.compile("wss?://(?:127\\.0\\.0\\.1|localhost)(?::\\d+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern FRAME_ANCESTORS = Pattern.compile("(?i)frame-ancestors\\s+[^;]+");
    private static final Set<String> STRIP_RESPONSE_HEADERS = Set.of("x-frame-options");

    private final ApplicationService applicationService;
    private final AgentTemplateRepository templateRepository;
    private final PeerUrlResolver peerUrlResolver;
    private final AuditService auditService;
    private final AgentRuntimeProperties runtimeProperties;
    private final OpenClawTrustedProxyHeaders trustedProxyHeaders;
    private final HttpClient httpClient = AgentUpstreamHttpClients.forProxy();

    public void proxy(Long appId, String portRef, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = applicationService.requireApplication(appId);
        validateAppRunning(app);
        validatePortRef(app, portRef);

        int containerPort = resolveContainerPort(app, portRef);
        String targetBase = resolveTargetBase(app, portRef, containerPort);
        String proxyPrefix = buildProxyPrefix(appId, portRef);
        String templateCode = resolveTemplateCode(app);
        boolean trustedProxy = trustedProxyHeaders.shouldInject(templateCode, portRef);
        String subPath = extractSubPath(request.getRequestURI(), appId, portRef);
        String query = sanitizeQuery(request.getQueryString());
        String targetUrl = targetBase + subPath + (query.isBlank() ? "" : "?" + query);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(60));
            String method = request.getMethod();
            if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                byte[] body = readRequestBody(request);
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            }
            request.getHeaderNames().asIterator().forEachRemaining(name -> {
                String lower = name.toLowerCase();
                if (HOP_BY_HOP.contains(lower) || "authorization".equals(lower)) {
                    return;
                }
                if (trustedProxy && trustedProxyHeaders.isInboundHeader(name)) {
                    return;
                }
                builder.header(name, request.getHeader(name));
            });
            if (trustedProxy) {
                trustedProxyHeaders.applyToRequestBuilder(builder, request);
            }

            HttpResponse<byte[]> upstream = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = upstream.statusCode();

            if (status >= 300 && status < 400) {
                String location = upstream.headers().firstValue("location").orElse(null);
                response.setStatus(status);
                response.setHeader("Location", rewriteUrl(location, proxyPrefix, targetBase));
                auditProxy(appId, portRef, targetUrl, request, trustedProxy);
                return;
            }

            byte[] body = upstream.body() == null ? new byte[0] : upstream.body();
            String contentType = upstream.headers().firstValue("content-type").orElse("");
            if (contentType.toLowerCase().contains("text/html")) {
                body = rewriteHtml(body, proxyPrefix, targetBase, appId, portRef);
            } else if (contentType.toLowerCase().contains("javascript")
                    || contentType.toLowerCase().contains("text/css")) {
                body = rewriteTextAssets(new String(body, StandardCharsets.UTF_8), proxyPrefix, targetBase, appId, portRef)
                        .getBytes(StandardCharsets.UTF_8);
            }

            response.setStatus(status);
            upstream.headers().map().forEach((name, values) -> {
                String lower = name.toLowerCase();
                if (HOP_BY_HOP.contains(lower) || STRIP_RESPONSE_HEADERS.contains(lower)) {
                    return;
                }
                if ("location".equalsIgnoreCase(name)) {
                    values.forEach(v -> response.addHeader(name, rewriteUrl(v, proxyPrefix, targetBase)));
                } else if ("content-security-policy".equals(lower)) {
                    values.forEach(v -> response.addHeader(name, rewriteContentSecurityPolicy(v)));
                } else {
                    values.forEach(v -> response.addHeader(name, v));
                }
            });
            response.setContentLength(body.length);
            try (OutputStream out = response.getOutputStream()) {
                out.write(body);
            }
            auditProxy(appId, portRef, targetUrl, request, trustedProxy);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeProxyError(response, "代理请求被中断");
        } catch (BusinessException e) {
            writeProxyError(response, e.getMessage());
        } catch (Exception e) {
            log.warn("Proxy failed appId={} portRef={} target={}: {}", appId, portRef, targetUrl, e.getMessage());
            writeProxyError(response, "无法连接 Agent Web 控制台: " + e.getMessage()
                    + "。请确认应用已部署且 Gateway 端口可访问。");
        }
    }

    public String buildProxyPath(Long appId, String consoleKey) {
        return buildProxyPrefix(appId, consoleKey) + "/";
    }

    public String buildProxyWsPrefix(Long appId, String portRef) {
        return "/api/apps/" + appId + "/proxy-ws/" + portRef;
    }

    public String resolveUpstreamWebSocketUrl(Long appId, String portRef, String requestUri) {
        Application app = applicationService.requireApplication(appId);
        validateAppRunning(app);
        validatePortRef(app, portRef);
        int containerPort = resolveContainerPort(app, portRef);
        String targetBase = resolveTargetBase(app, portRef, containerPort);
        String httpBase = targetBase.endsWith("/") ? targetBase.substring(0, targetBase.length() - 1) : targetBase;
        String wsBase = httpBase.replaceFirst("^http", "ws");
        String subPath = extractSubPath(requestUri, appId, portRef);
        return wsBase + subPath;
    }

    public Application requireRunningAppForProxy(Long appId, String portRef) {
        Application app = applicationService.requireApplication(appId);
        validateAppRunning(app);
        validatePortRef(app, portRef);
        return app;
    }

    public String resolveTemplateCode(Application app) {
        return templateRepository.findById(app.getTemplateId())
                .map(AgentTemplate::getCode)
                .orElse("");
    }

    private byte[] readRequestBody(HttpServletRequest request) throws IOException {
        try (var in = request.getInputStream()) {
            byte[] body = in.readNBytes(MAX_PROXY_BODY_BYTES + 1);
            if (body.length > MAX_PROXY_BODY_BYTES) {
                throw new BusinessException(413, "请求体过大");
            }
            return body;
        }
    }

    private void auditProxy(Long appId, String portRef, String targetUrl, HttpServletRequest request,
                            boolean trustedProxy) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("portRef", portRef);
        details.put("targetUrl", targetUrl);
        if (trustedProxy) {
            details.put("authMode", "trusted-proxy");
            details.put("upstreamUser", SecurityUtils.getCurrentUsername());
        }
        auditService.log(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUsername(),
                "proxy:access", "application", String.valueOf(appId),
                details, request.getRemoteAddr(), "success");
    }

    private void writeProxyError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        response.setContentType("text/html;charset=UTF-8");
        String html = """
                <!DOCTYPE html><html><head><meta charset="utf-8"><title>控制台不可用</title></head>
                <body style="font-family:sans-serif;padding:24px;color:#333;">
                <h3>Web 控制台暂时不可用</h3>
                <p>%s</p>
                <p style="color:#666;font-size:13px;">提示：请先部署应用；若已部署，可在「端口」Tab 确认 gateway 已暴露，或在新窗口直接访问 accessUrl。</p>
                </body></html>
                """.formatted(escapeHtml(message));
        response.getOutputStream().write(html.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildProxyPrefix(Long appId, String portRef) {
        return "/api/apps/" + appId + "/proxy/" + portRef;
    }

    private String resolveTargetBase(Application app, String portRef, int containerPort) {
        String provider = app.getRuntimeProvider() != null ? app.getRuntimeProvider()
                : runtimeProperties.getProvider();
        Integer hostPort = resolveHostPort(app, portRef);
        boolean trustedProxy = trustedProxyHeaders.shouldInject(resolveTemplateCode(app), portRef);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(peerUrlResolver.resolveHttpPeerUrl(app.getId(), containerPort, provider));
        if ("docker".equals(provider) && hostPort != null) {
            candidates.add("http://host.docker.internal:" + hostPort);
            String accessHost = runtimeProperties.getDocker().getAccessHost();
            if (accessHost != null && !accessHost.isBlank()) {
                candidates.add("http://" + accessHost + ":" + hostPort);
            }
            candidates.add("http://127.0.0.1:" + hostPort);
        }
        for (String candidate : candidates) {
            if (probe(candidate, trustedProxy)) {
                log.debug("Proxy target selected appId={} portRef={} url={}", app.getId(), portRef, candidate);
                return candidate;
            }
        }
        return candidates.iterator().next();
    }

    static boolean isSuccessfulProbeStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 400;
    }

    private boolean probe(String baseUrl, boolean trustedProxy) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(4));
            if (trustedProxy) {
                trustedProxyHeaders.applyToRequestBuilder(builder, null);
            }
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return isSuccessfulProbeStatus(response.statusCode());
        } catch (Exception e) {
            return false;
        }
    }

    static String rewriteContentSecurityPolicy(String value) {
        if (value == null || value.isBlank()) {
            return "frame-ancestors 'self'";
        }
        if (FRAME_ANCESTORS.matcher(value).find()) {
            return FRAME_ANCESTORS.matcher(value).replaceAll("frame-ancestors 'self'");
        }
        return value + "; frame-ancestors 'self'";
    }

    private Integer resolveHostPort(Application app, String portRef) {
        if (app.getPorts() == null) {
            return null;
        }
        for (Map<String, Object> port : app.getPorts()) {
            if (portRef.equals(String.valueOf(port.get("name")))) {
                Object hostPort = port.get("hostPort");
                if (hostPort instanceof Number number) {
                    return number.intValue();
                }
            }
        }
        return null;
    }

    private String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            if ("token".equalsIgnoreCase(key)) {
                continue;
            }
            parts.add(pair);
        }
        return String.join("&", parts);
    }

    private String rewriteUrl(String url, String proxyPrefix, String targetBase) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith(proxyPrefix)) {
            return url;
        }
        if (url.startsWith("/")) {
            return proxyPrefix + url;
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getRawPath() == null ? "/" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            if (url.startsWith(targetBase)) {
                String suffix = path + query;
                return proxyPrefix + (suffix.startsWith("/") ? suffix : "/" + suffix);
            }
            if (LOCAL_ORIGIN.matcher(url).find() || (uri.getHost() != null && uri.getHost().startsWith("app-"))) {
                return proxyPrefix + path + query;
            }
        } catch (Exception ignored) {
        }
        return LOCAL_ORIGIN.matcher(url).replaceAll(proxyPrefix);
    }

    private byte[] rewriteHtml(byte[] body, String proxyPrefix, String targetBase, Long appId, String portRef) {
        String html = rewriteTextAssets(new String(body, StandardCharsets.UTF_8), proxyPrefix, targetBase, appId, portRef);
        String baseTag = "<base href=\"" + proxyPrefix + "/\">";
        if (html.matches("(?is).*<head[^>]*>.*")) {
            html = html.replaceFirst("(?i)<head([^>]*)>", "<head$1>" + baseTag);
        } else if (html.matches("(?is).*<html[^>]*>.*")) {
            html = html.replaceFirst("(?i)<html([^>]*)>", "<html$1><head>" + baseTag + "</head>");
        } else {
            html = baseTag + html;
        }
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private String rewriteTextAssets(String content, String proxyPrefix, String targetBase, Long appId, String portRef) {
        String wsProxyPrefix = buildProxyWsPrefix(appId, portRef);
        String httpWsTarget = targetBase.replaceFirst("^http", "ws");
        String rewritten = content.replace(targetBase, proxyPrefix + "/");
        rewritten = rewritten.replace(httpWsTarget, wsProxyPrefix);
        rewritten = LOCAL_ORIGIN.matcher(rewritten).replaceAll(proxyPrefix);
        rewritten = LOCAL_WS_ORIGIN.matcher(rewritten).replaceAll(wsProxyPrefix);
        rewritten = rewritten.replaceAll(
                "(wss?)://([^/]+)" + Pattern.quote("/api/apps/" + appId + "/proxy/" + portRef),
                "$1://$2" + wsProxyPrefix);
        return rewritten;
    }

    private void validateAppRunning(Application app) {
        if (app.getRuntimeRef() == null || app.getRuntimeRef().isBlank()) {
            throw new BusinessException("应用尚未部署");
        }
        if (!"running".equals(app.getStatus())) {
            throw new BusinessException("应用未在运行中");
        }
    }

    @SuppressWarnings("unchecked")
    private void validatePortRef(Application app, String portRef) {
        AgentTemplate template = templateRepository.findById(app.getTemplateId())
                .orElseThrow(() -> new BusinessException("模板不存在"));
        Map<String, Object> schema = template.getManagementSchema();
        if (schema == null || schema.isEmpty()) {
            throw new BusinessException("模板未配置 Web 控制台");
        }
        List<Map<String, Object>> consoles = (List<Map<String, Object>>) schema.get("webConsoles");
        if (consoles == null) {
            throw new BusinessException("未授权的代理端口: " + portRef);
        }
        boolean allowed = consoles.stream()
                .anyMatch(c -> portRef.equals(String.valueOf(c.get("key")))
                        || portRef.equals(String.valueOf(c.get("portRef"))));
        if (!allowed) {
            throw new BusinessException("未授权的代理端口: " + portRef);
        }
    }

    private int resolveContainerPort(Application app, String portRef) {
        if (app.getPorts() == null) {
            throw new BusinessException("应用未配置端口");
        }
        for (Map<String, Object> port : app.getPorts()) {
            if (portRef.equals(String.valueOf(port.get("name")))) {
                return ((Number) port.get("containerPort")).intValue();
            }
        }
        throw new BusinessException("端口不存在: " + portRef);
    }

    private String extractSubPath(String requestUri, Long appId, String portRef) {
        String httpPrefix = "/api/apps/" + appId + "/proxy/" + portRef;
        String wsPrefix = "/api/apps/" + appId + "/proxy-ws/" + portRef;
        for (String prefix : List.of(wsPrefix, httpPrefix)) {
            if (requestUri == null || !requestUri.startsWith(prefix)) {
                continue;
            }
            if (requestUri.length() <= prefix.length()) {
                return "/";
            }
            String sub = requestUri.substring(prefix.length());
            return sub.isBlank() ? "/" : sub;
        }
        return "/";
    }
}
