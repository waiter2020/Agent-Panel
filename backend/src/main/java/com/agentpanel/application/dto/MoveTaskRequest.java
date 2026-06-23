package com.agentpanel.application.dto;

import lombok.Data;

@Data
public class MoveTaskRequest {
    private Long columnId;
    private Integer orderNo;
}
