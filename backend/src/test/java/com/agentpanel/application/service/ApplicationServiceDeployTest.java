package com.agentpanel.application.service;

import com.agentpanel.application.entity.Application;
import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.repository.*;
import com.agentpanel.common.CryptoService;
import com.agentpanel.auth.ApiKeyManagementService;
import com.agentpanel.config.AgentPanelProperties;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.RuntimeProviderFactory;
import com.agentpanel.runtime.docker.DockerHostDataPathResolver;
import com.agentpanel.runtime.api.*;
import com.agentpanel.system.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceDeployTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private AgentTemplateRepository templateRepository;
    @Mock private AppEnvRepository appEnvRepository;
    @Mock private AppDeploymentRepository deploymentRepository;
    @Mock private AgentTopologyRepository topologyRepository;
    @Mock private AgentTopologyNodeRepository topologyNodeRepository;
    @Mock private RuntimeProviderFactory runtimeProviderFactory;
    @Mock private AgentRuntimeProperties runtimeProperties;
    @Mock private AgentPanelProperties panelProperties;
    @Mock private ApiKeyManagementService apiKeyManagementService;
    @Mock private CryptoService cryptoService;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;
    @Mock private AgentRuntimeProvider runtimeProvider;
    @Mock private DockerHostDataPathResolver dockerHostDataPathResolver;
    @Mock private TaskKanbanService taskKanbanService;
    @Mock private OpenClawGatewayBootstrapService openClawGatewayBootstrapService;

    @InjectMocks
    private ApplicationService applicationService;

    private Application app;
    private AgentTemplate template;

    @BeforeEach
    void setUp() {
        app = new Application();
        app.setId(1L);
        app.setName("test-app");
        app.setTemplateId(1L);
        app.setImage("nginx");
        app.setTag("latest");
        app.setReplicas(1);
        app.setRuntimeProvider("k8s");
        app.setPorts(new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                "name", "gateway",
                "containerPort", 8080,
                "protocol", "TCP",
                "expose", true
        )))));
        app.setVolumes(List.of());
        app.setResources(Map.of("cpu", "1", "memory", "1Gi"));

        template = new AgentTemplate();
        template.setId(1L);
        template.setName("test");
        template.setDefaultResources(Map.of("cpu", "1", "memory", "1Gi"));
        template.setVolumeSchema(List.of());
        template.setPortSchema(app.getPorts());

        AgentRuntimeProperties.K8s k8s = new AgentRuntimeProperties.K8s();
        k8s.setAccessHost("node1.local");
        AgentRuntimeProperties.Docker docker = new AgentRuntimeProperties.Docker();
        docker.setDataRoot("/data/apps");
        when(runtimeProperties.getK8s()).thenReturn(k8s);
        lenient().when(runtimeProperties.getDocker()).thenReturn(docker);

        var principal = new com.agentpanel.auth.AuthPrincipal(1L, "admin", 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void deployWritesBackNodePortAccessUrls() {
        when(applicationRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(app));
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(appEnvRepository.findByApplicationId(1L)).thenReturn(List.of());
        when(runtimeProviderFactory.get("k8s")).thenReturn(runtimeProvider);

        List<PortMapping> resolved = List.of(new PortMapping("gateway", 8080, 31234, "TCP", true));
        DeployResult result = new DeployResult(new RuntimeRef("k8s", "app-1", "agentpanel-apps"),
                "running", "ok", resolved);
        when(runtimeProvider.deploy(any())).thenReturn(result);
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(Map.of());

        var dto = applicationService.deploy(1L, mock(jakarta.servlet.http.HttpServletRequest.class));

        assertEquals(31234, ((Number) dto.getPorts().get(0).get("hostPort")).intValue());
        assertFalse(dto.getAccessUrls().isEmpty());
        assertEquals("node1.local:31234", dto.getAccessUrls().get(0).get("url"));
        verify(deploymentRepository).save(any());
    }
}
