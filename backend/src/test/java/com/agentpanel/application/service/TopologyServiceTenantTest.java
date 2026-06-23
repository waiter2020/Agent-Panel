package com.agentpanel.application.service;

import com.agentpanel.application.dto.AddTopologyNodeRequest;
import com.agentpanel.application.dto.CreateTopologyRequest;
import com.agentpanel.application.entity.AgentTopology;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.*;
import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.common.BusinessException;
import com.agentpanel.system.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopologyServiceTenantTest {

    @Mock private AgentTopologyRepository topologyRepository;
    @Mock private AgentTopologyNodeRepository nodeRepository;
    @Mock private AgentLinkRepository linkRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationService applicationService;
    @Mock private TopologyLinkService topologyLinkService;
    @Mock private com.agentpanel.auth.repository.ApiKeyRepository apiKeyRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private TopologyService topologyService;

    @BeforeEach
    void setUp() {
        var principal = new AuthPrincipal(1L, "user", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAllowsSameNameInDifferentTenantScope() {
        CreateTopologyRequest createRequest = new CreateTopologyRequest();
        createRequest.setName("shared-name");

        when(topologyRepository.existsByNameAndTenantIdAndDeletedFalse("shared-name", 2L)).thenReturn(false);
        when(topologyRepository.save(any())).thenAnswer(inv -> {
            AgentTopology t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(nodeRepository.findByTopologyId(1L)).thenReturn(List.of());
        when(linkRepository.findByTopologyId(1L)).thenReturn(List.of());

        var httpRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        assertDoesNotThrow(() -> topologyService.create(createRequest, httpRequest));

        verify(topologyRepository).existsByNameAndTenantIdAndDeletedFalse("shared-name", 2L);
    }

    @Test
    void addNodeRejectsCrossTenantApplication() {
        AgentTopology topology = new AgentTopology();
        topology.setId(1L);
        topology.setTenantId(2L);
        topology.setName("topo");

        Application foreignApp = new Application();
        foreignApp.setId(100L);
        foreignApp.setTenantId(99L);
        foreignApp.setName("foreign");

        AddTopologyNodeRequest request = new AddTopologyNodeRequest();
        request.setApplicationId(100L);
        request.setRole("member");

        when(topologyRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(topology));
        when(applicationRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(foreignApp));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> topologyService.addNode(1L, request, null));
        assertEquals("应用不存在", ex.getMessage());
        verify(nodeRepository, never()).save(any());
    }
}
