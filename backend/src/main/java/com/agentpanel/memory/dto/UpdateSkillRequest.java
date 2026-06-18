package com.agentpanel.memory.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateSkillRequest {
    private String name;
    private String description;
    private String content;
    private Long applicationId;
    private Map<String, Object> metadata;
}
