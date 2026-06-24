package com.agentpanel.application.service;

import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.config.OpenClawProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenClawTrustedProxyHeadersTest {

    private final OpenClawProperties properties = new OpenClawProperties();
    private final OpenClawTrustedProxyHeaders headers = new OpenClawTrustedProxyHeaders(properties);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldInjectOnlyForOpenClawGateway() {
        assertTrue(headers.shouldInject("openclaw", "gateway"));
        assertFalse(headers.shouldInject("openclaw", "dashboard"));
        assertFalse(headers.shouldInject("hermes", "gateway"));
    }

    @Test
    void buildUpstreamHeadersUsesCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthPrincipal(1L, "alice", 1L), null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.addHeader("Host", "panel.example.com");

        Map<String, String> upstream = headers.buildUpstreamHeaders(request);

        assertEquals("alice", upstream.get(OpenClawProperties.DEFAULT_USER_HEADER));
        assertEquals("1", upstream.get(OpenClawProperties.DEFAULT_PROXY_MARKER_HEADER));
        assertEquals("https", upstream.get("X-Forwarded-Proto"));
        assertEquals("panel.example.com", upstream.get("X-Forwarded-Host"));
        assertEquals(OpenClawTrustedProxyHeaders.DEFAULT_OPERATOR_SCOPES,
                upstream.get(OpenClawTrustedProxyHeaders.OPENCLAW_SCOPES_HEADER));
    }

    @Test
    void buildUpstreamHeadersFromHandshakeUsesProvidedHost() {
        Map<String, String> upstream = headers.buildUpstreamHeadersFromHandshake("localhost:8080", "http");
        assertEquals("localhost:8080", upstream.get("X-Forwarded-Host"));
        assertEquals("http", upstream.get("X-Forwarded-Proto"));
    }

    @Test
    void isInboundHeaderMatchesConfiguredNames() {
        assertTrue(headers.isInboundHeader("X-Agentpanel-User"));
        assertTrue(headers.isInboundHeader("x-agentpanel-proxy"));
        assertFalse(headers.isInboundHeader("Authorization"));
    }
}
