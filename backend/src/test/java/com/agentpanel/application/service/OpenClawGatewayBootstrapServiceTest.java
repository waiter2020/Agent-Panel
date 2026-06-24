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
    @Mock private OpenClawTrustedProxyCidrResolver trustedProxyCidrResolver;

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
                trustedProxyCidrResolver,
                objectMapper
        );
        when(trustedProxyCidrResolver.resolve(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(List.of("172.23.0.0/16", "127.0.0.1/32", "::1/128"));
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
        @SuppressWarnings("unchecked")
        Map<String, Object> controlUi = (Map<String, Object>) gateway.get("controlUi");
        assertNotNull(controlUi);
        @SuppressWarnings("unchecked")
        List<String> origins = (List<String>) controlUi.get("allowedOrigins");
        assertFalse(origins.isEmpty());
        assertEquals(Boolean.TRUE, controlUi.get("dangerouslyAllowHostHeaderOriginFallback"));
        assertEquals(Boolean.TRUE, controlUi.get("allowInsecureAuth"));
        assertEquals(Boolean.TRUE, controlUi.get("dangerouslyDisableDeviceAuth"));
        @SuppressWarnings("unchecked")
        Map<String, Object> trustedProxy = (Map<String, Object>) auth.get("trustedProxy");
        assertEquals(Boolean.TRUE, trustedProxy.get("allowLoopback"));
        assertNull(auth.get("token"));
    }

    @Test
    void mergeGatewayConfigRemovesStaleAuthToken() throws Exception {
        String existing = """
                {
                  "gateway": {
                    "auth": {
                      "mode": "token",
                      "token": "old-secret-token"
                    }
                  }
                }
                """;
        String merged = bootstrapService.mergeGatewayConfig(existing, "docker");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(merged, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) ((Map<String, Object>) root.get("gateway")).get("auth");
        assertEquals("trusted-proxy", auth.get("mode"));
        assertNull(auth.get("token"));
        assertNull(auth.get("password"));
    }

    @Test
    void mergeGatewayConfigOverridesStaleControlUiOrigins() throws Exception {
        String existing = """
                {
                  "gateway": {
                    "controlUi": {
                      "allowedOrigins": ["http://localhost:18789"]
                    }
                  }
                }
                """;
        String merged = bootstrapService.mergeGatewayConfig(existing, "docker");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(merged, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> gateway = (Map<String, Object>) root.get("gateway");
        @SuppressWarnings("unchecked")
        Map<String, Object> controlUi = (Map<String, Object>) gateway.get("controlUi");
        @SuppressWarnings("unchecked")
        List<String> origins = (List<String>) controlUi.get("allowedOrigins");
        assertTrue(origins.contains("http://localhost:8080"));
        assertFalse(origins.contains("http://localhost:18789"));
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
    void bootstrapDockerVolumeAtPathWritesConfig(@TempDir Path tempDir) throws Exception {
        bootstrapService.bootstrapDockerVolumeAtPath(7L, "data", tempDir);
        assertTrue(Files.exists(tempDir.resolve("openclaw.json")));
        verify(volumePermissionService).prepareVolumeDirectory(tempDir);
    }

    @Test
    void mergeGatewayConfigOverridesStaleTrustedProxies() throws Exception {
        String existing = """
                {
                  "gateway": {
                    "trustedProxies": ["172.17.0.0/16"]
                  }
                }
                """;
        String merged = bootstrapService.mergeGatewayConfig(existing, "docker", "agentpanel-net");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(merged, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> gateway = (Map<String, Object>) root.get("gateway");
        @SuppressWarnings("unchecked")
        List<String> trustedProxies = (List<String>) gateway.get("trustedProxies");
        assertTrue(trustedProxies.contains("172.23.0.0/16"));
        assertFalse(trustedProxies.contains("172.17.0.0/16"));
    }

    @Test
    void buildBootstrapJsonIncludesK8sCidrs() throws Exception {
        when(trustedProxyCidrResolver.resolve("k8s", null))
                .thenReturn(List.of("10.42.0.0/16", "127.0.0.1/32"));
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
