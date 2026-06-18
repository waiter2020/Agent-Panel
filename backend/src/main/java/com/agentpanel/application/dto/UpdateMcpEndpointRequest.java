package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMcpEndpointRequest {
    private String url;
    private Boolean enabled;
}
