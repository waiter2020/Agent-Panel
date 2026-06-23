package com.agentpanel.application.service;

import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.config.OpenClawProperties;
import com.agentpanel.runtime.docker.DockerHostDataPathResolver;
import com.agentpanel.runtime.docker.DockerVolumePermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenClawGatewayBootstrapServiceTest {

    @Mock private AgentTemplateRepository templateRepository;
    @Mock private AgentRuntimeProperties runtimeProperties;
    @Mock private DockerHostDataPathResolver dockerHostDataPathResolver;
    @Mock private DockerVolumePermissionService volumePermissionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenClawProperties openClawProperties;

    private OpenClawGatewayBootstrapService bootstrapService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        openClawProperties = new OpenClawProperties();
        bootstrapService = new OpenClawGatewayBootstrapService(
                templateRepository,
                runtimeProperties,
                openClawProperties,
                dockerHostDataPathResolver,
                volumePermissionService,
                objectMapper
        );
    }

    @Test
    void mergeGatewayConfigPreservesExistingChannels() throws Exception {
        String existing = """
                {
                  "channels": {"telegram": {"enabled": true}},
                  "gateway": {"bind": "loopback"}
                }
                """;
        String merged = bootstrapService.mergeGatewayConfig(existing, "docker");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(merged, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> channels = (Map<String, Object>) root.get("channels");
        @SuppressWarnings("unchecked")
        Map<String, Object> gateway = (Map<String, Object>) root.get("gateway");

        assertNotNull(channels);
        assertEquals("lan", gateway.get("bind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) gateway.get("auth");
        assertEquals("trusted-proxy", auth.get("mode"));
    }

    @Test
    void bootstrapDockerVolumeWritesConfig(@TempDir Path tempDir) throws Exception {
        Application app = openClawApp();
        when(templateRepository.findById(10L)).thenReturn(Optional.of(openClawTemplate()));
        when(runtimeProperties.getProvider()).thenReturn("docker");
        when(dockerHostDataPathResolver.toPanelVolumePath(7L, "data")).thenReturn(tempDir);

        bootstrapService.bootstrapBeforeDeploy(app);

        Path config = tempDir.resolve("openclaw.json");
        assertTrue(Files.exists(config));
        String content = Files.readString(config);
        assertTrue(content.contains("trusted-proxy"));
        verify(volumePermissionService).prepareVolumeDirectory(tempDir);
    }

    @Test
    void buildBootstrapJsonIncludesK8sCidrs() throws Exception {
        openClawProperties.getK8s().setTrustedProxyCidrs(List.of("10.42.0.0/16"));
        String json = bootstrapService.buildBootstrapJson("k8s");
        assertTrue(json.contains("10.42.0.0/16"));
        assertTrue(json.contains("trusted-proxy"));
    }

    private Application openClawApp() {
        Application app = new Application();
        app.setId(7L);
        app.setTemplateId(10L);
        app.setVolumes(List.of(Map.of("name", "data", "containerPath", "/home/node/.openclaw")));
        return app;
    }

    private AgentTemplate openClawTemplate() {
        AgentTemplate template = new AgentTemplate();
        template.setId(10L);
        template.setCode("openclaw");
        return template;
    }
}
