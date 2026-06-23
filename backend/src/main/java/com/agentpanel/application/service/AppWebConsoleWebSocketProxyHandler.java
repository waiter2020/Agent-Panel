package com.agentpanel.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppWebConsoleWebSocketProxyHandler extends AbstractWebSocketHandler {

    private final AppWebConsoleProxyService proxyService;
    private final OpenClawTrustedProxyHeaders trustedProxyHeaders;
    private final StandardWebSocketClient upstreamClient = new StandardWebSocketClient();
    private final Map<String, WebSocketSession> upstreamSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession browserSession) throws Exception {
        Object auth = browserSession.getAttributes().get("authentication");
        if (auth instanceof UsernamePasswordAuthenticationToken token) {
            SecurityContextHolder.getContext().setAuthentication(token);
        }
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
        String upstreamUrl = proxyService.resolveUpstreamWebSocketUrl(appId, portRef, requestUri);

        org.springframework.web.socket.WebSocketHttpHeaders headers = new org.springframework.web.socket.WebSocketHttpHeaders();
        if (trustedProxy) {
            trustedProxyHeaders.buildUpstreamHeaders(null).forEach(headers::add);
        }

        WebSocketSession decoratedBrowser = new ConcurrentWebSocketSessionDecorator(browserSession, 10_000, 512 * 1024);
        upstreamClient.execute(new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession upstreamSession) {
                upstreamSessions.put(browserSession.getId(), upstreamSession);
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
                log.warn("Web 控制台上游 WS 错误 appId={} portRef={}: {}", appId, portRef, exception.getMessage());
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
                log.warn("Web 控制台上游 WS 连接失败 appId={} portRef={} url={}: {}",
                        appId, portRef, upstreamUrl, error.getMessage());
                closeQuietly(browserSession, CloseStatus.SERVER_ERROR);
            }
        });
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
        closeQuietly(upstream, status);
        SecurityContextHolder.clearContext();
    }

    @Override
    public void handleTransportError(WebSocketSession browserSession, Throwable exception) {
        log.warn("Web 控制台浏览器 WS 错误 session={}: {}", browserSession.getId(), exception.getMessage());
        afterConnectionClosed(browserSession, CloseStatus.SERVER_ERROR);
    }

    private void relayToUpstream(WebSocketSession browserSession, WebSocketMessage<?> message) throws Exception {
        WebSocketSession upstream = upstreamSessions.get(browserSession.getId());
        if (upstream == null || !upstream.isOpen()) {
            return;
        }
        synchronized (upstream) {
            upstream.sendMessage(message);
        }
    }

    private void closePair(WebSocketSession browserSession, WebSocketSession upstreamSession, CloseStatus status) {
        upstreamSessions.remove(browserSession.getId());
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
