package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class AddTopologyLinkRequest {
    private Long fromNodeId;
    private Long toNodeId;
    private String protocol;
    private Map<String, Object> config;
}
