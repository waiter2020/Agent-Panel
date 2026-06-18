package com.agentpanel.application.service;

import com.agentpanel.application.dto.TopologyDto;
import com.agentpanel.application.entity.AgentTopology;
import com.agentpanel.application.entity.AgentTopologyNode;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.*;
import com.agentpanel.auth.ApiKeyManagementService;
import com.agentpanel.auth.repository.ApiKeyRepository;
import com.agentpanel.auth.dto.CreateApiKeyResponse;
import com.agentpanel.common.CryptoService;
import com.agentpanel.config.AgentPanelProperties;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.RuntimeProviderFactory;
import com.agentpanel.runtime.api.AgentRuntimeProvider;
import com.agentpanel.runtime.api.DeployResult;
import com.agentpanel.runtime.api.RuntimeRef;
import com.agentpanel.runtime.docker.DockerRuntimeProvider;
import com.agentpanel.memory.repository.SharedSkillRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopologyServiceTest {

    @Mock private AgentTopologyRepository topologyRepository;
    @Mock private AgentTopologyNodeRepository topologyNodeRepository;
    @Mock private AgentLinkRepository linkRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private PeerUrlResolver peerUrlResolver;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private AgentTemplateRepository templateRepository;
    @Mock private AppEnvRepository appEnvRepository;
    @Mock private AppDeploymentRepository deploymentRepository;
    @Mock private RuntimeProviderFactory runtimeProviderFactory;
    @Mock private AgentRuntimeProperties runtimeProperties;
    @Mock private AgentPanelProperties panelProperties;
    @Mock private ApiKeyManagementService apiKeyManagementService;
    @Mock private CryptoService cryptoService;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;
    @Mock private SharedSkillRepository sharedSkillRepository;
    @Mock private DockerRuntimeProvider dockerProvider;

    @InjectMocks
    private ApplicationService applicationService;

    private AgentTopology topology;
    private AgentTopologyNode node;
    private Application app;

    @BeforeEach
    void setUp() {
        topology = new AgentTopology();
        topology.setId(1L);
        topology.setName("test-topology");
        topology.setNetworkName("topo-net");
        topology.setStatus("draft");

        node = new AgentTopologyNode();
        node.setId(10L);
        node.setTopologyId(1L);
        node.setApplicationId(100L);
        node.setRole("gateway");

        app = new Application();
        app.setId(100L);
        app.setName("gateway-app");
        app.setTemplateId(1L);
        app.setImage("nginx");
        app.setTag("v1.0.0");
        app.setReplicas(1);
        app.setRuntimeProvider("docker");
        app.setPorts(List.of(Map.of("name", "http", "containerPort", 80, "protocol", "TCP", "expose", true)));
        app.setVolumes(List.of());
        app.setResources(Map.of("cpu", "1", "memory", "1Gi"));

        var principal = new com.agentpanel.auth.AuthPrincipal(1L, "admin", 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        AgentRuntimeProperties.Docker docker = new AgentRuntimeProperties.Docker();
        docker.setDataRoot("/data/apps");
        docker.setNetwork("agentpanel-net");
        lenient().when(runtimeProperties.getProvider()).thenReturn("docker");
        lenient().when(runtimeProperties.getDocker()).thenReturn(docker);
        lenient().when(panelProperties.getInferenceUrl()).thenReturn("http://agent-panel:8080/v1");
        lenient().when(linkRepository.findByTopologyId(1L)).thenReturn(List.of());
        lenient().when(sharedSkillRepository.findByTopologyIdOrderByNameAsc(1L)).thenReturn(List.of());
        lenient().when(peerUrlResolver.resolveHttpPeerUrl(any())).thenReturn("http://app-100:80");
    }

    @Test
    void deployTopologyInjectsInferenceAndUsesSharedNetwork() throws Exception {
        when(topologyRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(topology));
        when(topologyNodeRepository.findByTopologyId(1L)).thenReturn(List.of(node));
        when(linkRepository.findByTopologyId(1L)).thenReturn(List.of());
        when(applicationRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(app));
        when(appEnvRepository.findByApplicationId(100L)).thenReturn(List.of());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(mockTemplate()));
        when(runtimeProviderFactory.get("docker")).thenReturn(dockerProvider);
        when(apiKeyManagementService.create(any())).thenAnswer(inv -> {
            CreateApiKeyResponse response = new CreateApiKeyResponse();
            response.setId(5L);
            response.setRawKey("apk_test_inference_key_1234567890");
            response.setName("test-topology-inference");
            return response;
        });
        when(cryptoService.encrypt(anyString())).thenReturn("encrypted");
        when(dockerProvider.deploy(any())).thenReturn(
                new DeployResult(new RuntimeRef("docker", "container-1", null), "running", "ok", List.of()));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(topologyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(Map.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        TopologyDto result = applicationService.deployTopology(1L, mock(jakarta.servlet.http.HttpServletRequest.class));

        assertEquals("deployed", result.getStatus());
        assertEquals("apk_test_inference_key_1234567890", result.getInferenceKeyRaw());
        verify(dockerProvider).ensureNetwork("topo-net");
        verify(appEnvRepository, atLeastOnce()).save(argThat(env ->
                "OPENAI_BASE_URL".equals(env.getKey())
                        || "OPENAI_API_KEY".equals(env.getKey())
                        || "AGENTPANEL_DELEGATION_WEBHOOK".equals(env.getKey())
                        || "AGENTPANEL_MEMORY_API".equals(env.getKey())
                        || "AGENTPANEL_API_KEY".equals(env.getKey())));
        verify(dockerProvider).deploy(argThat(spec -> "topo-net".equals(spec.network())));
    }

    private com.agentpanel.application.entity.AgentTemplate mockTemplate() {
        var template = new com.agentpanel.application.entity.AgentTemplate();
        template.setId(1L);
        template.setDefaultResources(Map.of("cpu", "1", "memory", "1Gi"));
        template.setVolumeSchema(List.of());
        return template;
    }
}
