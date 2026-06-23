package com.agentpanel.application.dto;

import lombok.Data;

@Data
public class PortUsageDto {
    private Long appId;
    private String appName;
    private String templateCode;
    private String portName;
    private Integer containerPort;
    private Integer hostPort;
    private Boolean expose;
    private String accessUrl;
}
