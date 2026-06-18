package com.agentpanel.system.dto;

import lombok.Data;

@Data
public class PermissionDto {
    private Long id;
    private String code;
    private String name;
    private String type;
    private Long parentId;
}
