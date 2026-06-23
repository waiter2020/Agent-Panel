package com.agentpanel.auth.dto;

import lombok.Data;

@Data
public class ProxySessionRequest {
    private Long appId;
    private String consoleKey;
}
