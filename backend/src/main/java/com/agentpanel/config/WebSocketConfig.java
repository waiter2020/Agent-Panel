package com.agentpanel.config;

import com.agentpanel.terminal.AppTerminalWebSocketHandler;
import com.agentpanel.terminal.TerminalWebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AppTerminalWebSocketHandler terminalWebSocketHandler;
    private final TerminalWebSocketInterceptor terminalWebSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler, "/api/apps/{appId}/terminal/ws")
                .addInterceptors(terminalWebSocketInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
