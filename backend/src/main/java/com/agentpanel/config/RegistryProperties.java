package com.agentpanel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "agent.registry")
public class RegistryProperties {

    private Duration cacheTtl = Duration.ofMinutes(10);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private List<String> allowedHosts = new ArrayList<>(List.of(
            "ghcr.io",
            "registry-1.docker.io",
            "docker.io",
            "index.docker.io"
    ));
}
