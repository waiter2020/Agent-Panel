package com.agentpanel.application.service;

import com.agentpanel.config.WebSocketBufferConfig;
import lombok.RequiredArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.net.URI;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppWebConsoleWebSocketProxyHandler extends AbstractWebSocketHandler {

    private static final int MAX_PENDING_MESSAGES = 128;

    private final AppWebConsoleProxyService proxyService;
    private final OpenClawTrustedProxyHeaders trustedProxyHeaders;
    private final OpenClawOriginResolver openClawOriginResolver;
    private final OpenClawConnectMessageSanitizer connectMessageSanitizer;
    private final StandardWebSocketClient upstreamClient;
    private final Map<String, WebSocketSession> upstreamSessions = new ConcurrentHashMap<>();
    private final Map<String, Queue<WebSocketMessage<?>>> pendingBrowserMessages = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession browserSession) throws Exception {
        Authentication previousAuth = SecurityContextHolder.getContext().getAuthentication();
        Object auth = browserSession.getAttributes().get("authentication");
        if (auth instanceof UsernamePasswordAuthenticationToken token) {
            SecurityContextHolder.getContext().setAuthentication(token);
        }

        String sessionId = browserSession.getId();
        try {
            Long appId = (Long) browserSession.getAttributes().get("appId");
            String portRef = (String) browserSession.getAttributes().get("portRef");
            String requestUri = (String) browserSession.getAttributes().get("requestUri");
            if (appId == null || portRef == null || requestUri == null) {
                browserSession.close(CloseStatus.BAD_DATA);
                return;
            }

            var app = proxyService.requireRunningAppForProxy(appId, portRef);
            String templateCode = proxyService.resolveTemplateCode(app);
            boolean trustedProxy = trustedProxyHeaders.shouldInject(templateCode, portRef);
            browserSession.getAttributes().put("trustedProxy", trustedProxy);

            String upstreamUrl = proxyService.resolveUpstreamWebSocketUrl(appId, portRef, requestUri);
            var headers = new org.springframework.web.socket.WebSocketHttpHeaders();
            if (trustedProxy) {
                String host = (String) browserSession.getAttributes().get("forwardedHost");
                String proto = (String) browserSession.getAttributes().get("forwardedProto");
                trustedProxyHeaders.buildUpstreamHeadersFromHandshake(host, proto).forEach(headers::add);
                String origin = openClawOriginResolver.resolveForBrowserSession(browserSession);
                if (origin != null && !origin.isBlank()) {
                    headers.add("Origin", origin);
                }
            }

            pendingBrowserMessages.put(sessionId, new ConcurrentLinkedQueue<>());
            WebSocketSession decoratedBrowser = new ConcurrentWebSocketSessionDecorator(
                    browserSession,
                    WebSocketBufferConfig.WEB_CONSOLE_SEND_TIME_LIMIT_MS,
                    WebSocketBufferConfig.WEB_CONSOLE_SEND_BUFFER_LIMIT);
            upstreamClient.execute(new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession upstreamSession) {
                    upstreamSessions.put(sessionId, upstreamSession);
                    flushPendingBrowserMessages(browserSession, upstreamSession);
                }

                @Override
                public void handleMessage(WebSocketSession upstreamSession, WebSocketMessage<?> message) throws Exception {
                    if (!decoratedBrowser.isOpen()) {
                        return;
                    }
                    synchronized (decoratedBrowser) {
                        if (message instanceof TextMessage textMessage) {
                            decoratedBrowser.sendMessage(textMessage);
                        } else if (message instanceof BinaryMessage binaryMessage) {
                            decoratedBrowser.sendMessage(binaryMessage);
                        } else if (message instanceof PongMessage pongMessage) {
                            decoratedBrowser.sendMessage(pongMessage);
                        }
                    }
                }

                @Override
                public void handleTransportError(WebSocketSession upstreamSession, Throwable exception) {
                    log.warn("Web console upstream WS error appId={} portRef={}: {}", appId, portRef, exception.getMessage());
                    closePair(browserSession, upstreamSession, CloseStatus.SERVER_ERROR);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession upstreamSession, CloseStatus status) {
                    closePair(browserSession, upstreamSession, status);
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            }, headers, URI.create(upstreamUrl)).whenComplete((upstreamSession, error) -> {
                if (error != null) {
                    pendingBrowserMessages.remove(sessionId);
                    log.warn("Web console upstream WS connection failed appId={} portRef={} url={}: {}",
                            appId, portRef, upstreamUrl, error.getMessage());
                    closeQuietly(browserSession, CloseStatus.SERVER_ERROR);
                }
            });
        } catch (Exception e) {
            pendingBrowserMessages.remove(sessionId);
            throw e;
        } finally {
            if (previousAuth == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuth);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession browserSession, TextMessage message) throws Exception {
        relayToUpstream(browserSession, message);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession browserSession, BinaryMessage message) throws Exception {
        relayToUpstream(browserSession, message);
    }

    @Override
    protected void handlePongMessage(WebSocketSession browserSession, PongMessage message) throws Exception {
        relayToUpstream(browserSession, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession browserSession, CloseStatus status) {
        WebSocketSession upstream = upstreamSessions.remove(browserSession.getId());
        pendingBrowserMessages.remove(browserSession.getId());
        closeQuietly(upstream, status);
    }

    @Override
    public void handleTransportError(WebSocketSession browserSession, Throwable exception) {
        log.warn("Web console browser WS error session={}: {}", browserSession.getId(), exception.getMessage());
        afterConnectionClosed(browserSession, CloseStatus.SERVER_ERROR);
    }

    private void relayToUpstream(WebSocketSession browserSession, WebSocketMessage<?> message) throws Exception {
        WebSocketSession upstream = upstreamSessions.get(browserSession.getId());
        if (upstream == null || !upstream.isOpen()) {
            enqueuePendingMessage(browserSession, message);
            return;
        }
        sendToUpstream(browserSession, upstream, message);
    }

    private void enqueuePendingMessage(WebSocketSession browserSession, WebSocketMessage<?> message) throws Exception {
        Queue<WebSocketMessage<?>> queue = pendingBrowserMessages.get(browserSession.getId());
        if (queue == null) {
            return;
        }
        if (queue.size() >= MAX_PENDING_MESSAGES) {
            browserSession.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        queue.add(message);
    }

    private void flushPendingBrowserMessages(WebSocketSession browserSession, WebSocketSession upstreamSession) {
        Queue<WebSocketMessage<?>> queue = pendingBrowserMessages.get(browserSession.getId());
        if (queue == null) {
            return;
        }
        WebSocketMessage<?> message;
        while ((message = queue.poll()) != null && upstreamSession.isOpen()) {
            try {
                sendToUpstream(browserSession, upstreamSession, message);
            } catch (Exception e) {
                log.warn("Web console pending WS message relay failed session={}: {}", browserSession.getId(), e.getMessage());
                closePair(browserSession, upstreamSession, CloseStatus.SERVER_ERROR);
                return;
            }
        }
    }

    private void sendToUpstream(WebSocketSession browserSession, WebSocketSession upstream,
                                WebSocketMessage<?> message) throws Exception {
        WebSocketMessage<?> outbound = message;
        if (message instanceof TextMessage textMessage
                && Boolean.TRUE.equals(browserSession.getAttributes().get("trustedProxy"))) {
            String sanitized = connectMessageSanitizer.sanitizeIfConnect(textMessage.getPayload());
            if (!sanitized.equals(textMessage.getPayload())) {
                outbound = new TextMessage(sanitized);
            }
        }
        synchronized (upstream) {
            upstream.sendMessage(outbound);
        }
    }

    private void closePair(WebSocketSession browserSession, WebSocketSession upstreamSession, CloseStatus status) {
        upstreamSessions.remove(browserSession.getId());
        pendingBrowserMessages.remove(browserSession.getId());
        closeQuietly(browserSession, status);
        if (upstreamSession != null && upstreamSession.isOpen()) {
            closeQuietly(upstreamSession, status);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (Exception ignored) {
        }
    }
}
