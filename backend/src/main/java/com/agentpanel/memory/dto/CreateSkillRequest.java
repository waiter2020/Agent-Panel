package com.agentpanel.memory.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CreateSkillRequest {
    private Long topologyId;
    private Long applicationId;
    private String name;
    private String description;
    private String content;
    private Map<String, Object> metadata;
}
