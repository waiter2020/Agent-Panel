package com.agentpanel.auth.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ApiKeyDto {
    private Long id;
    private String name;
    private String keyPrefix;
    private boolean enabled;
    private List<String> scopes;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private boolean deprecated;
}
