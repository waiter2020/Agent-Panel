package com.agentpanel.application.service;

import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.config.OpenClawProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenClawTrustedProxyCidrResolverTest {

    @Test
    void resolveAlwaysIncludesLoopbackCidrs() {
        AgentRuntimeProperties runtimeProperties = new AgentRuntimeProperties();
        runtimeProperties.getDocker().setNetwork("agentpanel-net");
        OpenClawProperties openClawProperties = new OpenClawProperties();
        OpenClawTrustedProxyCidrResolver resolver =
                new OpenClawTrustedProxyCidrResolver(runtimeProperties, openClawProperties);

        List<String> cidrs = resolver.resolve("docker", "agentpanel-net");

        assertTrue(cidrs.contains("127.0.0.1/32"));
        assertTrue(cidrs.contains("::1/128"));
    }

    @Test
    void resolveMergesConfiguredExtras() {
        AgentRuntimeProperties runtimeProperties = new AgentRuntimeProperties();
        OpenClawProperties openClawProperties = new OpenClawProperties();
        openClawProperties.setTrustedProxies(List.of("10.0.0.0/8"));
        openClawProperties.getK8s().setAutoDiscoverNodePodCidrs(false);
        OpenClawTrustedProxyCidrResolver resolver =
                new OpenClawTrustedProxyCidrResolver(runtimeProperties, openClawProperties);

        List<String> cidrs = resolver.resolve("k8s", null);

        assertTrue(cidrs.contains("10.0.0.0/8"));
    }

    @Test
    void resolveIncludesDeployNetworkNameInDockerMode() {
        AgentRuntimeProperties runtimeProperties = new AgentRuntimeProperties();
        runtimeProperties.getDocker().setNetwork("default-net");
        OpenClawProperties openClawProperties = new OpenClawProperties();
        OpenClawTrustedProxyCidrResolver resolver =
                new OpenClawTrustedProxyCidrResolver(runtimeProperties, openClawProperties);

        List<String> cidrs = resolver.resolve("docker", "topology-net");

        assertFalse(cidrs.isEmpty());
        assertTrue(cidrs.contains("127.0.0.1/32"));
    }
}
