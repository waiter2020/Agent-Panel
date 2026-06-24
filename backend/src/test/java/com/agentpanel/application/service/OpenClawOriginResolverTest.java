package com.agentpanel.application.service;

import com.agentpanel.config.OpenClawProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenClawOriginResolverTest {

    private OpenClawProperties openClawProperties;
    private OpenClawOriginResolver resolver;

    @BeforeEach
    void setUp() {
        openClawProperties = new OpenClawProperties();
        openClawProperties.setPanelPublicOrigins(List.of(
                "http://localhost:8080",
                "http://127.0.0.1:8080"
        ));
        resolver = new OpenClawOriginResolver(openClawProperties);
    }

    @Test
    void resolveForBrowserSessionUsesRefererWhenOriginMissing() {
        WebSocketSession session = mock(WebSocketSession.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Referer", "http://localhost:8080/api/apps/6/proxy/gateway/");
        when(session.getHandshakeHeaders()).thenReturn(headers);

        assertEquals("http://localhost:8080", resolver.resolveForBrowserSession(session));
    }

    @Test
    void resolveForBrowserSessionUsesConfiguredFallback() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());

        assertEquals("http://localhost:8080", resolver.resolveForBrowserSession(session));
    }

    @Test
    void resolveFromHttpRequestBuildsOriginFromForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "panel.example.com");

        assertEquals("https://panel.example.com", resolver.resolveFromHttpRequest(request));
    }

    @Test
    void extractOriginFromUrlParsesAuthority() {
        assertEquals("http://127.0.0.1:8080",
                OpenClawOriginResolver.extractOriginFromUrl("http://127.0.0.1:8080/app/console/6/gateway"));
    }
}
