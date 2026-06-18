package com.agentpanel.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateApiKeyResponse extends ApiKeyDto {
    private String rawKey;
}
