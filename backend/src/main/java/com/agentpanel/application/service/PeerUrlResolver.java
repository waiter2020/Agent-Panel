package com.agentpanel.application.service;

import com.agentpanel.application.entity.Application;
import com.agentpanel.application.entity.McpEndpoint;
import com.agentpanel.application.repository.McpEndpointRepository;
import com.agentpanel.config.AgentRuntimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PeerUrlResolver {

    private final AgentRuntimeProperties runtimeProperties;
    private final McpEndpointRepository mcpEndpointRepository;

    public String resolveHttpPeerUrl(Application app) {
        int port = resolvePrimaryPort(app);
        return resolveHttpPeerUrl(app.getId(), port, app.getRuntimeProvider());
    }

    public String resolveHttpPeerUrl(Long applicationId, int port, String runtimeProvider) {
        String provider = runtimeProvider != null ? runtimeProvider : runtimeProperties.getProvider();
        if ("k8s".equals(provider)) {
            String namespace = runtimeProperties.getK8s().getNamespace();
            return "http://app-" + applicationId + "." + namespace + ".svc.cluster.local:" + port;
        }
        return "http://app-" + applicationId + ":" + port;
    }

    public String resolveMcpPeerUrl(Application app) {
        return mcpEndpointRepository.findByApplicationIdOrderByCreatedAtDesc(app.getId()).stream()
                .filter(McpEndpoint::isEnabled)
                .map(McpEndpoint::getUrl)
                .findFirst()
                .orElseGet(() -> resolveHttpPeerUrl(app) + "/mcp");
    }

    public int resolvePrimaryPort(Application app) {
        if (app.getPorts() == null || app.getPorts().isEmpty()) {
            return 80;
        }
        for (Map<String, Object> port : app.getPorts()) {
            if (Boolean.TRUE.equals(port.get("expose"))) {
                return ((Number) port.get("containerPort")).intValue();
            }
        }
        return ((Number) app.getPorts().getFirst().get("containerPort")).intValue();
    }

    public String resolveEnvKeyForTemplate(String templateCode, String protocol) {
        String prefix = templateCode == null ? "PEER" : templateCode.trim().toUpperCase().replace('-', '_');
        if ("mcp".equals(protocol)) {
            return "MCP_" + prefix + "_URL";
        }
        return prefix + "_URL";
    }

    public String resolveEnvKeyFromConfig(Map<String, Object> config, String templateCode, String protocol) {
        if (config != null && config.get("envKey") != null && !String.valueOf(config.get("envKey")).isBlank()) {
            return String.valueOf(config.get("envKey")).trim();
        }
        return resolveEnvKeyForTemplate(templateCode, protocol);
    }
}
