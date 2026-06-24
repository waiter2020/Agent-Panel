package com.agentpanel.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenClawConnectMessageSanitizerTest {

    private final OpenClawConnectMessageSanitizer sanitizer =
            new OpenClawConnectMessageSanitizer(new ObjectMapper());

    @Test
    void sanitizeIfConnectRemovesAuthFields() {
        String input = """
                {"type":"req","id":"1","method":"connect","params":{"auth":{"token":"stale"},"deviceToken":"x","scopes":["operator.read"],"client":{}}}
                """;
        String output = sanitizer.sanitizeIfConnect(input);
        assertFalse(output.contains("\"auth\""));
        assertFalse(output.contains("deviceToken"));
        assertTrue(output.contains("\"operator.read\""));
        assertTrue(output.contains("\"operator.write\""));
        assertTrue(output.contains("\"operator.admin\""));
        assertTrue(output.contains("\"method\":\"connect\""));
        assertTrue(output.contains("\"client\""));
    }

    @Test
    void sanitizeIfConnectLeavesNonConnectMessagesUntouched() {
        String input = "{\"type\":\"req\",\"method\":\"chat.send\",\"params\":{\"text\":\"hi\"}}";
        assertEquals(input, sanitizer.sanitizeIfConnect(input));
    }

    @Test
    void isConnectRequestDetectsReqEnvelope() {
        assertTrue(OpenClawConnectMessageSanitizer.isConnectRequest(
                new ObjectMapper().createObjectNode()
                        .put("type", "req")
                        .put("method", "connect")));
    }
}
