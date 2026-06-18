package com.agentpanel.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoleDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private List<Long> permissionIds;
}
