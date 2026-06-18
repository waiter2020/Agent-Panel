package com.agentpanel.memory.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class UpdateDelegationRequest {
    private String status;
    private Instant completedAt;
    private Map<String, Object> result;
}
