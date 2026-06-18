package com.agentpanel.system.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class TenantDto {
    private Long id;
    private String name;
    private String code;
    private Instant createdAt;
    private Instant updatedAt;
}
