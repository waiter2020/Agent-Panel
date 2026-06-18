package com.agentpanel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "agent.panel")
public class AgentPanelProperties {

    private String inferenceUrl = "http://agent-panel:8080/v1";
    private int apiKeyRotationGraceDays = 7;
}
