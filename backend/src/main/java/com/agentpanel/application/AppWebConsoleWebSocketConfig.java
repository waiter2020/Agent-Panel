package com.agentpanel.application;

import com.agentpanel.application.service.AppWebConsoleWebSocketProxyHandler;
import com.agentpanel.application.service.AppWebConsoleWebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class AppWebConsoleWebSocketConfig implements WebSocketConfigurer {

    private final AppWebConsoleWebSocketProxyHandler webConsoleWebSocketProxyHandler;
    private final AppWebConsoleWebSocketInterceptor webConsoleWebSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webConsoleWebSocketProxyHandler,
                        "/api/apps/{appId}/proxy/{portRef}",
                        "/api/apps/{appId}/proxy/{portRef}/",
                        "/api/apps/{appId}/proxy/{portRef}/*",
                        "/api/apps/{appId}/proxy/{portRef}/**",
                        "/api/apps/{appId}/proxy-ws/{portRef}",
                        "/api/apps/{appId}/proxy-ws/{portRef}/",
                        "/api/apps/{appId}/proxy-ws/{portRef}/*",
                        "/api/apps/{appId}/proxy-ws/{portRef}/**")
                .addInterceptors(webConsoleWebSocketInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
