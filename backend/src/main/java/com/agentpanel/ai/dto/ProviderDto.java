package com.agentpanel.ai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProviderDto {
    private Long id;
    private String code;
    private String name;
    private String type;
    private String baseUrl;
    private String apiKey;
    private boolean enabled;
    private Map<String, Object> config;
}
