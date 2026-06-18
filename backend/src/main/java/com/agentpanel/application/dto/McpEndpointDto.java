package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class McpEndpointDto {
    private Long id;
    private Long applicationId;
    private String applicationName;
    private String url;
    private List<Map<String, Object>> tools;
    private Map<String, Object> metadata;
    private boolean enabled;
    private Instant discoveredAt;
    private Instant createdAt;
    private Instant updatedAt;
}
