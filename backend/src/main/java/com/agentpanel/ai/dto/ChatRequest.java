package com.agentpanel.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    private Long providerId;
    private String model;
    private List<Message> messages;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
