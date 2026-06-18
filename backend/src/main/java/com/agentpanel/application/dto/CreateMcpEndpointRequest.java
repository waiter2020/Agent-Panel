package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMcpEndpointRequest {
    private Long applicationId;
    private String url;
    private Boolean enabled;
}
