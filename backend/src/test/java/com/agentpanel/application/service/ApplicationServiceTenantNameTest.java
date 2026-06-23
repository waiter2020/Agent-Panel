package com.agentpanel.application.service;

import com.agentpanel.application.dto.ApplicationDto;
import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.AgentTopology;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.*;
import com.agentpanel.auth.ApiKeyManagementService;
import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.auth.repository.ApiKeyRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.CryptoService;
import com.agentpanel.config.AgentPanelProperties;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.memory.repository.SharedSkillRepository;
import com.agentpanel.runtime.RuntimeProviderFactory;
import com.agentpanel.runtime.docker.DockerHostDataPathResolver;
import com.agentpanel.system.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTenantNameTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private AgentTemplateRepository templateRepository;
    @Mock private AppEnvRepository appEnvRepository;
    @Mock private AppDeploymentRepository deploymentRepository;
    @Mock private AgentTopologyRepository topologyRepository;
    @Mock private AgentTopologyNodeRepository topologyNodeRepository;
    @Mock private AgentLinkRepository linkRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private PeerUrlResolver peerUrlResolver;
    @Mock private RuntimeProviderFactory runtimeProviderFactory;
    @Mock private AgentRuntimeProperties runtimeProperties;
    @Mock private AgentPanelProperties panelProperties;
    @Mock private ApiKeyManagementService apiKeyManagementService;
    @Mock private CryptoService cryptoService;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;
    @Mock private SharedSkillRepository sharedSkillRepository;
    @Mock private DockerHostDataPathResolver dockerHostDataPathResolver;
    @Mock private TaskKanbanService taskKanbanService;
    @Mock private OpenClawGatewayBootstrapService openClawGatewayBootstrapService;

    @InjectMocks
    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        var principal = new AuthPrincipal(1L, "user", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        AgentRuntimeProperties.Docker docker = new AgentRuntimeProperties.Docker();
        docker.setAccessHost("localhost");
        lenient().when(runtimeProperties.getDocker()).thenReturn(docker);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createChecksNameWithinTenantOnly() {
        ApplicationDto dto = new ApplicationDto();
        dto.setName("my-agent");
        dto.setTemplateId(1L);
        dto.setImage("nginx:latest");

        AgentTemplate template = new AgentTemplate();
        template.setId(1L);
        template.setImage("nginx");
        template.setDefaultTag("latest");
        template.setPortSchema(List.of());
        template.setDefaultResources(Map.of("cpu", "1", "memory", "1Gi"));
        template.setVolumeSchema(List.of());

        when(applicationRepository.existsByNameAndTenantIdAndDeletedFalse("my-agent", 2L)).thenReturn(false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(applicationRepository.save(any())).thenAnswer(inv -> {
            Application app = inv.getArgument(0);
            app.setId(10L);
            return app;
        });

        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertDoesNotThrow(() -> applicationService.create(dto, request));

        verify(applicationRepository).existsByNameAndTenantIdAndDeletedFalse("my-agent", 2L);
    }

    @Test
    void createRejectsDuplicateNameInSameTenant() {
        ApplicationDto dto = new ApplicationDto();
        dto.setName("dup");
        dto.setTemplateId(1L);
        dto.setImage("nginx:latest");

        when(applicationRepository.existsByNameAndTenantIdAndDeletedFalse("dup", 2L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> applicationService.create(dto, null));
    }

    @Test
    void deployTopologyRejectsCrossTenantTopologyBeforeLoadingNodes() {
        AgentTopology topology = new AgentTopology();
        topology.setId(99L);
        topology.setName("foreign");
        topology.setTenantId(7L);
        when(topologyRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.of(topology));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> applicationService.deployTopology(99L, null));

        assertEquals("拓扑不存在", ex.getMessage());
        verify(topologyNodeRepository, never()).findByTopologyId(99L);
    }
}
