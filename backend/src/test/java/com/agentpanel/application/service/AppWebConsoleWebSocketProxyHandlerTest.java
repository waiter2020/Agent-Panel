package com.agentpanel.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppWebConsoleWebSocketProxyHandlerTest {

    @Mock private AppWebConsoleProxyService proxyService;
    @Mock private OpenClawTrustedProxyHeaders trustedProxyHeaders;
    @Mock private OpenClawOriginResolver openClawOriginResolver;
    @Mock private OpenClawConnectMessageSanitizer connectMessageSanitizer;
    @Mock private StandardWebSocketClient upstreamClient;

    @Test
    void messagesSentBeforeUpstreamConnectionAreFlushed() throws Exception {
        AppWebConsoleWebSocketProxyHandler handler = new AppWebConsoleWebSocketProxyHandler(
                proxyService, trustedProxyHeaders, openClawOriginResolver, connectMessageSanitizer, upstreamClient);
        WebSocketSession browser = org.mockito.Mockito.mock(WebSocketSession.class);
        WebSocketSession upstream = org.mockito.Mockito.mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        when(browser.getId()).thenReturn("browser-1");
        when(browser.getAttributes()).thenReturn(attrs);
        when(upstream.isOpen()).thenReturn(true);

        @SuppressWarnings("unchecked")
        Map<String, Queue<WebSocketMessage<?>>> pending =
                (Map<String, Queue<WebSocketMessage<?>>>) field(handler, "pendingBrowserMessages").get(handler);
        pending.put("browser-1", new ConcurrentLinkedQueue<>());

        Method relay = AppWebConsoleWebSocketProxyHandler.class
                .getDeclaredMethod("relayToUpstream", WebSocketSession.class, WebSocketMessage.class);
        relay.setAccessible(true);
        relay.invoke(handler, browser, new TextMessage("first"));
        assertEquals(1, pending.get("browser-1").size());

        @SuppressWarnings("unchecked")
        Map<String, WebSocketSession> upstreamSessions =
                (ConcurrentHashMap<String, WebSocketSession>) field(handler, "upstreamSessions").get(handler);
        upstreamSessions.put("browser-1", upstream);

        Method flush = AppWebConsoleWebSocketProxyHandler.class
                .getDeclaredMethod("flushPendingBrowserMessages", WebSocketSession.class, WebSocketSession.class);
        flush.setAccessible(true);
        flush.invoke(handler, browser, upstream);

        assertEquals(0, pending.get("browser-1").size());
        verify(upstream).sendMessage(any(TextMessage.class));
    }

    private Field field(Object target, String name) throws NoSuchFieldException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
