package com.agentpanel.memory.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class SharedMemoryDto {
    private Long id;
    private Long topologyId;
    private Long applicationId;
    private String scope;
    private String key;
    private String content;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Long createdBy;
    private Double score;
}
