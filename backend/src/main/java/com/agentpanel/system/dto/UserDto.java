package com.agentpanel.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String status;
    private Long tenantId;
    private String tenantName;
    private List<Long> roleIds;
}
