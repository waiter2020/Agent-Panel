package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class AddTopologyNodeRequest {
    private Long applicationId;
    private String role;
    private Map<String, Object> config;
}
