package com.agentpanel.application.dto;

import lombok.Data;

import java.util.List;

@Data
public class TaskColumnDto {
    private Long id;
    private String name;
    private String statusMapping;
    private int orderNo;
    private String color;
    private List<TaskItemDto> tasks;
}
