package com.agentpanel.runtime.api;

import java.util.List;
import java.util.Map;

public record DeploySpec(
        String name,
        String image,
        String tag,
        Map<String, String> env,
        Map<String, String> secretEnv,
        List<PortMapping> ports,
        List<VolumeMount> volumes,
        ResourceLimits resources,
        int replicas,
        Map<String, String> labels,
        String network,
        String namespace,
        boolean exposeViaIngress
) {
    public DeploySpec(String name, String image, String tag, Map<String, String> env,
                      Map<String, String> secretEnv, List<PortMapping> ports, List<VolumeMount> volumes,
                      ResourceLimits resources, int replicas, Map<String, String> labels) {
        this(name, image, tag, env, secretEnv, ports, volumes, resources, replicas, labels, null, null, false);
    }
    public String fullImage() {
        return tag == null || tag.isBlank() ? image : image + ":" + tag;
    }
}
