package com.agentpanel.application.service;

import com.agentpanel.application.dto.CreateMcpEndpointRequest;
import com.agentpanel.application.dto.McpEndpointDto;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.entity.McpEndpoint;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.application.repository.McpEndpointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpEndpointServiceTest {

    @Mock private McpEndpointRepository mcpEndpointRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private WebClient.Builder webClientBuilder;
    @InjectMocks private McpEndpointService mcpEndpointService;

    @Test
    void discoverPopulatesMockToolsWhenUnreachable() {
        McpEndpoint endpoint = new McpEndpoint();
        endpoint.setId(1L);
        endpoint.setApplicationId(10L);
        endpoint.setUrl("http://app-10:3000/mcp");
        Application app = new Application();
        app.setId(10L);
        app.setName("hermes");

        when(mcpEndpointRepository.findById(1L)).thenReturn(Optional.of(endpoint));
        when(mcpEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(app));
        when(webClientBuilder.build()).thenReturn(WebClient.create());

        McpEndpointDto result = mcpEndpointService.discover(1L);

        assertNotNull(result.getDiscoveredAt());
        assertEquals(3, result.getTools().size());
        assertEquals("search", result.getTools().getFirst().get("name"));
        assertEquals("stub", result.getMetadata().get("transport"));
    }

    @Test
    void createRequiresUrl() {
        CreateMcpEndpointRequest request = new CreateMcpEndpointRequest();
        request.setApplicationId(10L);
        assertThrows(com.agentpanel.common.BusinessException.class, () -> mcpEndpointService.create(request));
    }
}
