package com.agentpanel.memory.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class DelegationTraceDto {
    private Long id;
    private Long topologyId;
    private Long parentAppId;
    private String parentAppName;
    private Long childAppId;
    private String childAppName;
    private String taskSummary;
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private Map<String, Object> result;
}
