package com.agentpanel.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CurrentUserResponse {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private List<String> roles;
    private List<String> permissions;
    private List<MenuNode> menus;

    @Data
    @AllArgsConstructor
    public static class MenuNode {
        private Long id;
        private String name;
        private String path;
        private String icon;
        private String component;
        private List<MenuNode> children;
    }
}
