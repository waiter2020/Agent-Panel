package com.agentpanel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "agent.openclaw")
public class OpenClawProperties {

    public static final String DEFAULT_USER_HEADER = "x-agentpanel-user";
    public static final String DEFAULT_PROXY_MARKER_HEADER = "x-agentpanel-proxy";

    private String userHeader = DEFAULT_USER_HEADER;
    private String proxyMarkerHeader = DEFAULT_PROXY_MARKER_HEADER;
    private List<String> trustedProxies = new ArrayList<>(List.of("172.17.0.0/16"));
    private K8s k8s = new K8s();

    @Getter
    @Setter
    public static class K8s {
        private List<String> trustedProxyCidrs = new ArrayList<>(List.of("10.244.0.0/16"));
    }

    public List<String> resolveTrustedProxies(String runtimeProvider) {
        List<String> merged = new ArrayList<>();
        if (trustedProxies != null) {
            merged.addAll(trustedProxies);
        }
        if ("k8s".equals(runtimeProvider) && k8s != null && k8s.getTrustedProxyCidrs() != null) {
            merged.addAll(k8s.getTrustedProxyCidrs());
        }
        return merged.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }
}
