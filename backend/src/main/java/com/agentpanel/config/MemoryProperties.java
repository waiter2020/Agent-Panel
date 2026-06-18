package com.agentpanel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "agent.panel.memory")
public class MemoryProperties {

    private int embeddingDimensions = 1536;
}
