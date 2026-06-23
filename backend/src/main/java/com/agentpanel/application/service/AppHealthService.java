package com.agentpanel.application.service;

import com.agentpanel.application.dto.AppHealthDto;
import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentUpstreamHttpClients;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppHealthService {

    private final ApplicationService applicationService;
    private final AgentTemplateRepository templateRepository;
    private final PeerUrlResolver peerUrlResolver;
    private final AppWebConsoleProxyService proxyService;
    private final OpenClawTrustedProxyHeaders trustedProxyHeaders;
    private final HttpClient httpClient = AgentUpstreamHttpClients.forHealthCheck();

    @SuppressWarnings("unchecked")
    public AppHealthDto check(Long appId) {
        Application app = applicationService.requireApplication(appId);
        AppHealthDto dto = new AppHealthDto();
        AgentTemplate template = templateRepository.findById(app.getTemplateId())
                .orElseThrow(() -> new BusinessException("模板不存在"));
        Map<String, Object> schema = template.getManagementSchema();
        List<Map<String, Object>> consoles = schema != null && schema.get("webConsoles") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        List<Map<String, Object>> consoleLinks = new ArrayList<>();
        for (Map<String, Object> console : consoles) {
            String key = String.valueOf(console.get("key"));
            consoleLinks.add(Map.of(
                    "key", key,
                    "title", console.getOrDefault("title", key),
                    "proxyPath", proxyService.buildProxyPath(appId, key)
            ));
        }
        dto.setWebConsoles(consoleLinks);

        if (app.getRuntimeRef() == null || !"running".equals(app.getStatus())) {
            dto.setHealthy(false);
            dto.setMessage("应用未运行");
            dto.setStatusCode(0);
            return dto;
        }

        Map<String, Object> healthCheck = schema != null && schema.get("healthCheck") instanceof Map<?, ?> hc
                ? (Map<String, Object>) hc
                : null;
        if (healthCheck == null) {
            dto.setHealthy(true);
            dto.setMessage("容器运行中（未配置健康检查）");
            dto.setStatusCode(200);
            return dto;
        }

        String portRef = String.valueOf(healthCheck.get("portRef"));
        String path = String.valueOf(healthCheck.getOrDefault("path", "/"));
        int containerPort = resolveContainerPort(app, portRef);
        String base = peerUrlResolver.resolveHttpPeerUrl(app.getId(), containerPort, app.getRuntimeProvider());
        String url = base + (path.startsWith("/") ? path : "/" + path);
        dto.setCheckedUrl(url);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(5));
            if (trustedProxyHeaders.shouldInject(template.getCode(), portRef)) {
                trustedProxyHeaders.applyToRequestBuilder(builder, null);
            }
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            dto.setStatusCode(response.statusCode());
            dto.setHealthy(response.statusCode() >= 200 && response.statusCode() < 400);
            dto.setMessage(dto.isHealthy() ? "Agent 健康" : "Agent 返回异常状态: " + response.statusCode());
        } catch (Exception e) {
            dto.setHealthy(false);
            dto.setStatusCode(0);
            dto.setMessage("健康检查失败: " + e.getMessage());
        }
        return dto;
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
}
