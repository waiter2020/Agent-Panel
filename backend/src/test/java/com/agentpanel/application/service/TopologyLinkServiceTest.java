package com.agentpanel.application.service;

import com.agentpanel.application.dto.AddTopologyLinkRequest;
import com.agentpanel.application.dto.TopologyLinkDto;
import com.agentpanel.application.entity.*;
import com.agentpanel.application.repository.*;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.system.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopologyLinkServiceTest {

    @Mock private AgentLinkRepository linkRepository;
    @Mock private AgentTopologyRepository topologyRepository;
    @Mock private AgentTopologyNodeRepository nodeRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private McpEndpointRepository mcpEndpointRepository;
    @Mock private AuditService auditService;

    private PeerUrlResolver peerUrlResolver;
    @InjectMocks
    private TopologyLinkService topologyLinkService;

    @BeforeEach
    void setUp() {
        AgentRuntimeProperties props = new AgentRuntimeProperties();
        peerUrlResolver = new PeerUrlResolver(props, mcpEndpointRepository);
        topologyLinkService = new TopologyLinkService(
                linkRepository, topologyRepository, nodeRepository,
                applicationRepository, peerUrlResolver, auditService);
        var principal = new com.agentpanel.auth.AuthPrincipal(1L, "admin", 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void addLinkCreatesHttpLinkWithPeerUrl() {
        AgentTopology topology = new AgentTopology();
        topology.setId(1L);
        AgentTopologyNode gateway = node(10L, 1L, 100L, "gateway");
        AgentTopologyNode worker = node(11L, 1L, 200L, "worker");
        Application workerApp = app(200L, "hermes-worker", 2L);
        Application gatewayApp = app(100L, "openclaw-gw", 1L);
        workerApp.setPorts(List.of(Map.of("name", "gateway", "containerPort", 3000, "expose", true)));

        when(topologyRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(topology));
        when(nodeRepository.findByTopologyId(1L)).thenReturn(List.of(gateway, worker));
        when(linkRepository.findByTopologyId(1L)).thenReturn(List.of());
        when(linkRepository.save(any())).thenAnswer(inv -> {
            AgentLink link = inv.getArgument(0);
            link.setId(99L);
            return link;
        });
        when(applicationRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(workerApp));
        when(applicationRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(gatewayApp));

        AddTopologyLinkRequest request = new AddTopologyLinkRequest();
        request.setFromNodeId(10L);
        request.setToNodeId(11L);
        request.setProtocol("http");

        TopologyLinkDto result = topologyLinkService.addLink(1L, request,
                mock(jakarta.servlet.http.HttpServletRequest.class));

        assertEquals("http", result.getProtocol());
        assertEquals("http://app-200:3000", result.getPeerUrl());
        verify(linkRepository).save(argThat(link ->
                link.getFromNodeId().equals(10L) && link.getToNodeId().equals(11L)));
    }

    @Test
    void peerUrlResolverUsesK8sDns() {
        AgentRuntimeProperties props = new AgentRuntimeProperties();
        props.setProvider("k8s");
        PeerUrlResolver resolver = new PeerUrlResolver(props, mcpEndpointRepository);
        Application app = app(42L, "worker", 2L);
        app.setRuntimeProvider("k8s");
        app.setPorts(List.of(Map.of("containerPort", 8080, "expose", true)));

        String url = resolver.resolveHttpPeerUrl(app);
        assertEquals("http://app-42.agentpanel-apps.svc.cluster.local:8080", url);
        assertEquals("HERMES_URL", resolver.resolveEnvKeyForTemplate("hermes", "http"));
        assertEquals("MCP_HERMES_URL", resolver.resolveEnvKeyForTemplate("hermes", "mcp"));
    }

    private AgentTopologyNode node(Long id, Long topologyId, Long appId, String role) {
        AgentTopologyNode node = new AgentTopologyNode();
        node.setId(id);
        node.setTopologyId(topologyId);
        node.setApplicationId(appId);
        node.setRole(role);
        return node;
    }

    private Application app(Long id, String name, Long templateId) {
        Application app = new Application();
        app.setId(id);
        app.setName(name);
        app.setTemplateId(templateId);
        app.setRuntimeProvider("docker");
        return app;
    }
}
