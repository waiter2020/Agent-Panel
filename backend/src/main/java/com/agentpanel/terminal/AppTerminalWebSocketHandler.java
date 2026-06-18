package com.agentpanel.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppTerminalWebSocketHandler extends TextWebSocketHandler {

    private final AppTerminalService terminalService;
    private final ObjectMapper objectMapper;
    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long appId = (Long) session.getAttributes().get("appId");
        var authentication = session.getAttributes().get("authentication");
        if (authentication instanceof org.springframework.security.core.Authentication auth) {
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        TerminalSession terminal = terminalService.open(appId, new TerminalOutputHandler() {
            @Override
            public void onOutput(byte[] data) {
                sendJson(session, Map.of(
                        "type", "output",
                        "encoding", "base64",
                        "data", Base64.getEncoder().encodeToString(data)));
            }

            @Override
            public void onError(String message) {
                sendJson(session, Map.of("type", "error", "message", message));
            }

            @Override
            public void onClosed() {
                closeQuietly(session, CloseStatus.NORMAL);
            }
        });
        sessions.put(session.getId(), terminal);
        sendJson(session, Map.of("type", "connected", "message", "终端已连接"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        TerminalSession terminal = sessions.get(session.getId());
        if (terminal == null) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText();
            switch (type) {
                case "input" -> {
                    String data = node.path("data").asText("");
                    if (node.has("encoding") && "base64".equals(node.get("encoding").asText())) {
                        terminal.write(Base64.getDecoder().decode(data));
                    } else {
                        terminal.write(data.getBytes(StandardCharsets.UTF_8));
                    }
                }
                case "resize" -> terminal.resize(
                        node.path("cols").asInt(80),
                        node.path("rows").asInt(24));
                default -> sendJson(session, Map.of("type", "error", "message", "未知消息类型: " + type));
            }
        } catch (Exception e) {
            log.warn("终端消息处理失败 session={}: {}", session.getId(), e.getMessage());
            sendJson(session, Map.of("type", "error", "message", e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        TerminalSession terminal = sessions.remove(session.getId());
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception ignored) {
            }
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("终端 WebSocket 传输错误 session={}: {}", session.getId(), exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    private void sendJson(WebSocketSession session, Map<String, ?> payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.debug("发送终端消息失败: {}", e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ignored) {
        }
    }
}
