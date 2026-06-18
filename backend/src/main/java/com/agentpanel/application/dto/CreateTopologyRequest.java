package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTopologyRequest {
    private String name;
    private String description;
    private String networkName;
}
