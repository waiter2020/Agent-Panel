package com.agentpanel.config;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
public class WebSocketBufferConfig {

    public static final int MAX_TEXT_MESSAGE_BUFFER_SIZE = 8 * 1024 * 1024;
    public static final int MAX_BINARY_MESSAGE_BUFFER_SIZE = 8 * 1024 * 1024;
    public static final int WEB_CONSOLE_SEND_BUFFER_LIMIT = 8 * 1024 * 1024;
    public static final int WEB_CONSOLE_SEND_TIME_LIMIT_MS = 30_000;

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BUFFER_SIZE);
        container.setMaxSessionIdleTimeout(300_000L);
        return container;
    }

    @Bean
    public StandardWebSocketClient appWebConsoleUpstreamWebSocketClient() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setDefaultMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BUFFER_SIZE);
        return new StandardWebSocketClient(container);
    }
}
