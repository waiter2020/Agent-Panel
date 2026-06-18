package com.agentpanel.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyScopeMatcherTest {

    @Test
    void resolvesInferenceScope() {
        assertEquals("ai:chat", ApiKeyScopeMatcher.resolveRequiredScope("/v1/chat/completions", "POST"));
        assertTrue(ApiKeyScopeMatcher.hasRequiredScope("/v1/chat/completions", "POST", List.of("ai:chat")));
        assertFalse(ApiKeyScopeMatcher.hasRequiredScope("/v1/chat/completions", "POST", List.of("memory:read")));
    }

    @Test
    void resolvesMemoryScopes() {
        assertEquals("memory:read", ApiKeyScopeMatcher.resolveRequiredScope("/api/memory/search", "GET"));
        assertEquals("memory:write", ApiKeyScopeMatcher.resolveRequiredScope("/api/memory", "POST"));
        assertTrue(ApiKeyScopeMatcher.hasRequiredScope("/api/memory", "POST", List.of("memory:write")));
    }

    @Test
    void resolvesDelegationWebhook() {
        assertEquals("delegation:write",
                ApiKeyScopeMatcher.resolveRequiredScope("/api/delegations/webhook", "POST"));
        assertTrue(ApiKeyScopeMatcher.hasRequiredScope("/api/delegations/webhook", "POST",
                List.of("delegation:write")));
    }

    @Test
    void rejectsUnmanagedPaths() {
        assertFalse(ApiKeyScopeMatcher.isApiKeyManagedPath("/api/apps"));
        assertNull(ApiKeyScopeMatcher.resolveRequiredScope("/api/apps", "GET"));
    }
}
