package com.agentpanel.system.dto;

import lombok.Data;

@Data
public class MenuDto {
    private Long id;
    private String name;
    private String path;
    private String icon;
    private String component;
    private Long parentId;
    private int orderNo;
    private Long permissionId;
    private boolean hidden;
}
