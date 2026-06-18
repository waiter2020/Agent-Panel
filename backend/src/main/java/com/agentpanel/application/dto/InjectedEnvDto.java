package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InjectedEnvDto {
    private Long applicationId;
    private String applicationName;
    private String envKey;
    private String envValue;
    private String source;
}
