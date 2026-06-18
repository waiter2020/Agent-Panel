package com.agentpanel.memory.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class RecordDelegationRequest {
    private Long topologyId;
    private Long parentAppId;
    private Long childAppId;
    private String taskSummary;
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private Map<String, Object> result;
}
