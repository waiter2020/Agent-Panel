package com.agentpanel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "agent.openclaw")
public class OpenClawProperties {

    public static final String DEFAULT_USER_HEADER = "x-agentpanel-user";
    public static final String DEFAULT_PROXY_MARKER_HEADER = "x-agentpanel-proxy";

    private String userHeader = DEFAULT_USER_HEADER;
    private String proxyMarkerHeader = DEFAULT_PROXY_MARKER_HEADER;
    private List<String> trustedProxies = new ArrayList<>();
    private List<String> panelPublicOrigins = new ArrayList<>(List.of(
            "http://localhost:8080",
            "http://127.0.0.1:8080",
            "http://localhost"
    ));
    private K8s k8s = new K8s();

    public List<String> resolvePanelPublicOrigins() {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (panelPublicOrigins != null) {
            for (String origin : panelPublicOrigins) {
                if (origin != null && !origin.isBlank()) {
                    merged.add(origin.trim());
                }
            }
        }
        if (merged.isEmpty()) {
            merged.add("http://localhost:8080");
        }
        return List.copyOf(merged);
    }

    @Getter
    @Setter
    public static class K8s {
        private List<String> trustedProxyCidrs = new ArrayList<>();
        /** When true, discover node PodCIDR from the Kubernetes API at deploy time. */
        private boolean autoDiscoverNodePodCidrs = true;
    }

    /**
     * @deprecated use {@link com.agentpanel.application.service.OpenClawTrustedProxyCidrResolver}
     */
    @Deprecated
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
