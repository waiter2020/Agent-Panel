package com.agentpanel.memory.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class SharedSkillDto {
    private Long id;
    private Long topologyId;
    private String topologyName;
    private Long applicationId;
    private String applicationName;
    private String name;
    private String description;
    private String content;
    private String filePath;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
