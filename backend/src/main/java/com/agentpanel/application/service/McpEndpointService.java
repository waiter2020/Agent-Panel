package com.agentpanel.application.service;

import com.agentpanel.application.dto.CreateMcpEndpointRequest;
import com.agentpanel.application.dto.McpEndpointDto;
import com.agentpanel.application.dto.UpdateMcpEndpointRequest;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.entity.McpEndpoint;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.application.repository.McpEndpointRepository;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.TenantAccessHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class McpEndpointService {

    private static final Duration DISCOVER_TIMEOUT = Duration.ofSeconds(5);

    private final McpEndpointRepository mcpEndpointRepository;
    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public List<McpEndpointDto> listByApplication(Long applicationId) {
        ensureApplication(applicationId);
        return mcpEndpointRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<McpEndpointDto> listAll() {
        return mcpEndpointRepository.findAll().stream()
                .filter(endpoint -> canAccessApplication(endpoint.getApplicationId()))
                .map(this::toDto)
                .toList();
    }

    public McpEndpointDto get(Long id) {
        return toDto(findEndpoint(id));
    }

    @Transactional
    public McpEndpointDto create(CreateMcpEndpointRequest request) {
        if (request.getApplicationId() == null) {
            throw new BusinessException("请选择应用");
        }
        if (request.getUrl() == null || request.getUrl().isBlank()) {
            throw new BusinessException("MCP 服务 URL 不能为空");
        }
        ensureApplication(request.getApplicationId());
        McpEndpoint endpoint = new McpEndpoint();
        endpoint.setApplicationId(request.getApplicationId());
        endpoint.setUrl(request.getUrl().trim());
        endpoint.setEnabled(request.getEnabled() == null || request.getEnabled());
        return toDto(mcpEndpointRepository.save(endpoint));
    }

    @Transactional
    public McpEndpointDto update(Long id, UpdateMcpEndpointRequest request) {
        McpEndpoint endpoint = findEndpoint(id);
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            endpoint.setUrl(request.getUrl().trim());
        }
        if (request.getEnabled() != null) {
            endpoint.setEnabled(request.getEnabled());
        }
        return toDto(mcpEndpointRepository.save(endpoint));
    }

    @Transactional
    public void delete(Long id) {
        findEndpoint(id);
        mcpEndpointRepository.deleteById(id);
    }

    @Transactional
    public McpEndpointDto discover(Long id) {
        McpEndpoint endpoint = findEndpoint(id);
        DiscoveredTools discovered = fetchToolsWithTransport(endpoint.getUrl());
        List<Map<String, Object>> tools = discovered.tools();
        String transport = discovered.transport();
        if (tools.isEmpty()) {
            log.info("MCP discover fallback to stub for endpoint {}", id);
            tools = stubTools();
            transport = "stub";
        }
        endpoint.setTools(tools);
        endpoint.setMetadata(Map.of("transport", transport));
        endpoint.setDiscoveredAt(Instant.now());
        return toDto(mcpEndpointRepository.save(endpoint));
    }

    List<Map<String, Object>> fetchTools(String url) {
        return fetchToolsWithTransport(url).tools();
    }

    DiscoveredTools fetchToolsWithTransport(String url) {
        String baseUrl = normalizeUrl(url);
        WebClient client = webClientBuilder.build();

        List<Map<String, Object>> tools = tryGetTools(client, baseUrl + "/tools");
        if (!tools.isEmpty()) {
            return new DiscoveredTools("http", tools);
        }
        tools = tryGetTools(client, baseUrl);
        if (!tools.isEmpty()) {
            return new DiscoveredTools("http", tools);
        }
        tools = tryJsonRpcTools(client, baseUrl);
        if (!tools.isEmpty()) {
            return new DiscoveredTools("jsonrpc", tools);
        }
        tools = trySseTools(baseUrl);
        if (!tools.isEmpty()) {
            return new DiscoveredTools("sse", tools);
        }
        return new DiscoveredTools("unknown", List.of());
    }

    private List<Map<String, Object>> tryGetTools(WebClient client, String uri) {
        try {
            String body = client.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(DISCOVER_TIMEOUT)
                    .block();
            return parseToolsBody(body);
        } catch (Exception e) {
            log.debug("MCP GET {} failed: {}", uri, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> tryJsonRpcTools(WebClient client, String baseUrl) {
        try {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "tools/list",
                    "params", Map.of()
            );
            String body = client.post()
                    .uri(baseUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(DISCOVER_TIMEOUT)
                    .block();
            return parseJsonRpcTools(body);
        } catch (Exception e) {
            log.debug("MCP JSON-RPC tools/list failed for {}: {}", baseUrl, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> trySseTools(String baseUrl) {
        for (String suffix : List.of("/sse", "/mcp/sse", "")) {
            List<Map<String, Object>> tools = readSseTools(baseUrl + suffix);
            if (!tools.isEmpty()) {
                return tools;
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> readSseTools(String uri) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(DISCOVER_TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(DISCOVER_TIMEOUT)
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                int linesRead = 0;
                while ((line = reader.readLine()) != null && linesRead++ < 80) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) {
                        continue;
                    }
                    List<Map<String, Object>> tools = parseToolsBody(payload);
                    if (!tools.isEmpty()) {
                        return tools;
                    }
                    tools = parseJsonRpcTools(payload);
                    if (!tools.isEmpty()) {
                        return tools;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("MCP SSE {} failed: {}", uri, e.getMessage());
        }
        return List.of();
    }

    private List<Map<String, Object>> parseToolsBody(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isArray()) {
                return parseToolNodes(root);
            }
            if (root.has("tools")) {
                return parseToolNodes(root.get("tools"));
            }
            if (root.has("result")) {
                return parseJsonRpcTools(body);
            }
        } catch (Exception e) {
            log.debug("Failed to parse MCP tools response: {}", e.getMessage());
        }
        return List.of();
    }

    private List<Map<String, Object>> parseJsonRpcTools(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.get("result");
            if (result == null) {
                return List.of();
            }
            if (result.has("tools")) {
                return parseToolNodes(result.get("tools"));
            }
            if (result.isArray()) {
                return parseToolNodes(result);
            }
        } catch (Exception e) {
            log.debug("Failed to parse MCP JSON-RPC response: {}", e.getMessage());
        }
        return List.of();
    }

    private List<Map<String, Object>> parseToolNodes(JsonNode toolsNode) {
        if (toolsNode == null || !toolsNode.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> tools = new ArrayList<>();
        for (JsonNode node : toolsNode) {
            Map<String, Object> tool = new LinkedHashMap<>();
            if (node.has("name")) {
                tool.put("name", node.get("name").asText());
            } else if (node.has("tool")) {
                tool.put("name", node.get("tool").asText());
            }
            if (node.has("description")) {
                tool.put("description", node.get("description").asText());
            }
            if (node.has("inputSchema")) {
                tool.put("inputSchema", objectMapper.convertValue(node.get("inputSchema"), new TypeReference<>() {}));
            }
            if (!tool.isEmpty()) {
                tools.add(tool);
            }
        }
        return tools;
    }

    private String normalizeUrl(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private List<Map<String, Object>> stubTools() {
        return List.of(
                tool("search", "搜索网络或知识库"),
                tool("read_file", "读取文件内容"),
                tool("write_file", "写入文件内容")
        );
    }

    private Map<String, Object> tool(String name, String description) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        return tool;
    }

    private McpEndpoint findEndpoint(Long id) {
        McpEndpoint endpoint = mcpEndpointRepository.findById(id)
                .orElseThrow(() -> new BusinessException("MCP 端点不存在"));
        ensureApplication(endpoint.getApplicationId());
        return endpoint;
    }

    private Application ensureApplication(Long applicationId) {
        Application app = applicationRepository.findByIdAndDeletedFalse(applicationId)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        TenantAccessHelper.requireOwnedTenant(app.getTenantId(), "应用不存在");
        return app;
    }

    private boolean canAccessApplication(Long applicationId) {
        return applicationRepository.findByIdAndDeletedFalse(applicationId)
                .filter(app -> SecurityUtils.isSuperAdmin()
                        || SecurityUtils.getCurrentTenantId().equals(app.getTenantId()))
                .isPresent();
    }

    private McpEndpointDto toDto(McpEndpoint endpoint) {
        McpEndpointDto dto = new McpEndpointDto();
        dto.setId(endpoint.getId());
        dto.setApplicationId(endpoint.getApplicationId());
        applicationRepository.findByIdAndDeletedFalse(endpoint.getApplicationId())
                .ifPresent(app -> dto.setApplicationName(app.getName()));
        dto.setUrl(endpoint.getUrl());
        dto.setTools(endpoint.getTools());
        dto.setMetadata(endpoint.getMetadata());
        dto.setEnabled(endpoint.isEnabled());
        dto.setDiscoveredAt(endpoint.getDiscoveredAt());
        dto.setCreatedAt(endpoint.getCreatedAt());
        dto.setUpdatedAt(endpoint.getUpdatedAt());
        return dto;
    }

    record DiscoveredTools(String transport, List<Map<String, Object>> tools) {
    }
}
