package com.agentpanel.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class OpenClawPropertiesBindingTest {

    @Test
    void bindsHelmOpenClawProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "agent.openclaw.trusted-proxies[0]=10.0.0.0/8",
                        "agent.openclaw.trusted-proxies[1]=192.168.0.0/16",
                        "agent.openclaw.panel-public-origins[0]=https://panel.example.com",
                        "agent.openclaw.panel-public-origins[1]=http://localhost:8080",
                        "agent.openclaw.k8s.trusted-proxy-cidrs[0]=10.244.0.0/16",
                        "agent.openclaw.k8s.auto-discover-node-pod-cidrs=false"
                )
                .run(context -> {
                    OpenClawProperties properties = context.getBean(OpenClawProperties.class);

                    assertEquals("10.0.0.0/8", properties.getTrustedProxies().get(0));
                    assertEquals("192.168.0.0/16", properties.getTrustedProxies().get(1));
                    assertTrue(properties.resolvePanelPublicOrigins().contains("https://panel.example.com"));
                    assertEquals("10.244.0.0/16", properties.getK8s().getTrustedProxyCidrs().getFirst());
                    assertFalse(properties.getK8s().isAutoDiscoverNodePodCidrs());
                });
    }

    @Configuration
    @EnableConfigurationProperties(OpenClawProperties.class)
    static class TestConfig {
    }
}
