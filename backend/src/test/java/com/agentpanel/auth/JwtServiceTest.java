package com.agentpanel.auth;

import com.agentpanel.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-must-be-at-least-32-bytes-long",
                "30m",
                "7d"
        );
        jwtService = new JwtService(properties);
    }

    @Test
    void generateAndParseAccessToken() {
        var user = new com.agentpanel.system.entity.SysUser();
        user.setId(1L);
        user.setUsername("admin");

        String token = jwtService.generateAccessToken(user, java.util.List.of("admin"), java.util.List.of("app:read"));
        assertNotNull(token);

        var claims = jwtService.parseToken(token);
        assertEquals("1", claims.getSubject());
        assertEquals("admin", claims.get("username"));
        assertEquals(1L, jwtService.getUserId(token));
    }

    @Test
    void generateRefreshTokenIsUnique() {
        String a = jwtService.generateRefreshToken();
        String b = jwtService.generateRefreshToken();
        assertNotEquals(a, b);
        assertTrue(a.length() > 32);
    }

    @Test
    void generateProxyTokenIncludesTenantIdAndRoles() {
        var user = new com.agentpanel.system.entity.SysUser();
        user.setId(2L);
        user.setUsername("tenant-user");

        String token = jwtService.generateProxyToken(
                user,
                java.util.List.of("user"),
                java.util.List.of("app:read"),
                42L,
                3L,
                "gateway");
        var claims = jwtService.parseToken(token);
        assertEquals("proxy", claims.get("type"));
        assertEquals(42L, claims.get("appId", Long.class));
        assertEquals(3L, claims.get("tenantId", Long.class));
        assertEquals("gateway", claims.get("consoleKey", String.class));
        assertEquals(java.util.List.of("user"), claims.get("roles", java.util.List.class));
    }

    @Test
    void generateSseTokenIncludesTenantId() {
        var user = new com.agentpanel.system.entity.SysUser();
        user.setId(5L);
        user.setUsername("tenant-user");
        user.setTenantId(4L);

        String token = jwtService.generateSseToken(user, java.util.List.of("app:read"));
        var claims = jwtService.parseToken(token);
        assertEquals("sse", claims.get("type"));
        assertEquals(4L, claims.get("tenantId", Long.class));
    }
}
