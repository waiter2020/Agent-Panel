package com.agentpanel.auth;

public record AuthPrincipal(Long userId, String username, Long tenantId) {
}
