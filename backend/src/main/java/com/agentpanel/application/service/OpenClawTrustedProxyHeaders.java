package com.agentpanel.application.service;

import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.config.OpenClawProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OpenClawTrustedProxyHeaders {

    public static final String OPENCLAW_SCOPES_HEADER = "x-openclaw-scopes";
    public static final String DEFAULT_OPERATOR_SCOPES =
            "operator.admin,operator.read,operator.write";
    public static final List<String> DEFAULT_OPERATOR_SCOPE_LIST = List.of(
            "operator.admin",
            "operator.read",
            "operator.write"
    );

    private static final Set<String> STRIP_FROM_INBOUND = Set.of(
            OpenClawProperties.DEFAULT_USER_HEADER,
            OpenClawProperties.DEFAULT_PROXY_MARKER_HEADER,
            "x-forwarded-user",
            "x-forwarded-proto",
            "x-forwarded-host",
            OPENCLAW_SCOPES_HEADER
    );

    private final OpenClawProperties openClawProperties;

    public boolean shouldInject(String templateCode, String portRef) {
        return OpenClawGatewayBootstrapService.OPENCLAW_TEMPLATE_CODE.equals(templateCode)
                && "gateway".equals(portRef);
    }

    public boolean isInboundHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        String lower = headerName.toLowerCase();
        if (STRIP_FROM_INBOUND.contains(lower)) {
            return true;
        }
        String userHeader = openClawProperties.getUserHeader();
        String markerHeader = openClawProperties.getProxyMarkerHeader();
        return lower.equals(userHeader.toLowerCase()) || lower.equals(markerHeader.toLowerCase());
    }

    public Map<String, String> buildUpstreamHeaders(HttpServletRequest request) {
        return buildUpstreamHeaders(
                request != null ? request.getHeader("Host") : null,
                resolveProto(request),
                true);
    }

    public Map<String, String> buildUpstreamHeadersFromHandshake(String host, String forwardedProto) {
        return buildUpstreamHeaders(host, forwardedProto, true);
    }

    private Map<String, String> buildUpstreamHeaders(String host, String proto, boolean includeScopes) {
        Map<String, String> headers = new LinkedHashMap<>();
        String username = SecurityUtils.getCurrentUsername();
        if (username == null || username.isBlank()) {
            username = "agentpanel-user";
        }
        headers.put(openClawProperties.getUserHeader(), username);
        headers.put(openClawProperties.getProxyMarkerHeader(), "1");
        if (proto == null || proto.isBlank()) {
            proto = "http";
        }
        headers.put("X-Forwarded-Proto", proto);
        if (host != null && !host.isBlank()) {
            headers.put("X-Forwarded-Host", host);
        }
        if (includeScopes) {
            headers.put(OPENCLAW_SCOPES_HEADER, DEFAULT_OPERATOR_SCOPES);
        }
        return headers;
    }

    private static String resolveProto(HttpServletRequest request) {
        if (request == null) {
            return "http";
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) {
            proto = request.isSecure() ? "https" : "http";
        }
        return proto;
    }

    public void applyToRequestBuilder(java.net.http.HttpRequest.Builder builder, HttpServletRequest request) {
        buildUpstreamHeaders(request).forEach(builder::header);
    }

    public List<String> upstreamHeaderNames() {
        return List.of(
                openClawProperties.getUserHeader(),
                openClawProperties.getProxyMarkerHeader(),
                "X-Forwarded-Proto",
                "X-Forwarded-Host",
                OPENCLAW_SCOPES_HEADER
        );
    }
}
