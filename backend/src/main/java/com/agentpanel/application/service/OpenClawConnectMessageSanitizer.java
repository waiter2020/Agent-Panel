package com.agentpanel.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenClawConnectMessageSanitizer {

    private static final String CONNECT_METHOD = "connect";

    private final ObjectMapper objectMapper;

    public String sanitizeIfConnect(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!isConnectRequest(root)) {
                return payload;
            }
            if (!(root instanceof ObjectNode objectNode)) {
                return payload;
            }
            JsonNode params = objectNode.get("params");
            if (!(params instanceof ObjectNode paramsNode)) {
                return payload;
            }
            paramsNode.remove("auth");
            paramsNode.remove("deviceToken");
            paramsNode.set("scopes", buildOperatorScopes());
            return objectMapper.writeValueAsString(objectNode);
        } catch (Exception ignored) {
            return payload;
        }
    }

    static boolean isConnectRequest(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        JsonNode type = root.get("type");
        if (type != null && "req".equalsIgnoreCase(type.asText())) {
            JsonNode method = root.get("method");
            return method != null && CONNECT_METHOD.equalsIgnoreCase(method.asText());
        }
        JsonNode method = root.get("method");
        return method != null && CONNECT_METHOD.equalsIgnoreCase(method.asText());
    }

    private ArrayNode buildOperatorScopes() {
        ArrayNode scopes = objectMapper.createArrayNode();
        for (String scope : OpenClawTrustedProxyHeaders.DEFAULT_OPERATOR_SCOPE_LIST) {
            scopes.add(scope);
        }
        return scopes;
    }
}
