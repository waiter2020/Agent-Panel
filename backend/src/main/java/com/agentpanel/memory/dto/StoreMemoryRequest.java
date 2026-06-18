package com.agentpanel.memory.dto;

import lombok.Data;

import java.util.Map;

@Data
public class StoreMemoryRequest {
    private String key;
    private String content;
    private String scope;
    private Long topologyId;
    private Long applicationId;
    private Map<String, Object> metadata;
}
