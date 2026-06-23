package com.agentpanel.application.dto;

import lombok.Data;

import java.util.List;

@Data
public class TaskBoardDto {
    private Long id;
    private String name;
    private String scopeType;
    private String scopeRef;
    private List<TaskColumnDto> columns;
}
