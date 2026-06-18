package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
public class TopologyLinkDto {
    private Long id;
    private Long topologyId;
    private Long fromNodeId;
    private Long toNodeId;
    private String fromApplicationName;
    private String toApplicationName;
    private String protocol;
    private Map<String, Object> config;
    private String peerUrl;
    private Instant createdAt;
}
