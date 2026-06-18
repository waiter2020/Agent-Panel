package com.agentpanel.auth.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class UpdateApiKeyRequest {
    private String name;
    private Boolean enabled;
    private List<String> scopes;
    private Instant expiresAt;
}
