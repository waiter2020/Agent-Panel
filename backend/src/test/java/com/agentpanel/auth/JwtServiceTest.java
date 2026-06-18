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
}
