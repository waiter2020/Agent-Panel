package com.agentpanel.application.dto;

import lombok.Data;

@Data
public class TaskItemDto {
    private Long id;
    private Long columnId;
    private Long applicationId;
    private String title;
    private String description;
    private String priority;
    private int orderNo;
    private String appName;
    private String appStatus;
    private String templateCode;
}
