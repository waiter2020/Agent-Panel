package com.agentpanel.application.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AppHealthDto {
    private boolean healthy;
    private String message;
    private int statusCode;
    private String checkedUrl;
    private List<Map<String, Object>> webConsoles;
}
