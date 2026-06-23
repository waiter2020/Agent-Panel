package com.agentpanel.application.service;

import com.agentpanel.application.dto.AppHealthDto;
import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppHealthServiceTest {

    @Mock private ApplicationService applicationService;
    @Mock private AgentTemplateRepository templateRepository;
    @Mock private PeerUrlResolver peerUrlResolver;
    @Mock private AppWebConsoleProxyService proxyService;
    @Mock private OpenClawTrustedProxyHeaders trustedProxyHeaders;

    @InjectMocks
    private AppHealthService appHealthService;

    @Test
    void checkReturnsUnhealthyWhenAppNotRunning() {
        Application app = new Application();
        app.setId(1L);
        app.setTemplateId(10L);
        app.setStatus("stopped");

        AgentTemplate template = new AgentTemplate();
        template.setId(10L);
        template.setManagementSchema(Map.of(
                "webConsoles", List.of(Map.of("key", "gateway", "title", "Gateway"))));

        when(applicationService.requireApplication(1L)).thenReturn(app);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(proxyService.buildProxyPath(1L, "gateway")).thenReturn("/api/apps/1/proxy/gateway/");

        AppHealthDto dto = appHealthService.check(1L);

        assertFalse(dto.isHealthy());
        assertEquals("应用未运行", dto.getMessage());
        assertEquals(1, dto.getWebConsoles().size());
    }

    @Test
    void checkReturnsHealthyWhenNoHealthCheckConfigured() {
        Application app = new Application();
        app.setId(1L);
        app.setTemplateId(10L);
        app.setStatus("running");
        app.setRuntimeRef("docker:abc");

        AgentTemplate template = new AgentTemplate();
        template.setId(10L);
        template.setManagementSchema(Map.of("webConsoles", List.of()));

        when(applicationService.requireApplication(1L)).thenReturn(app);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        AppHealthDto dto = appHealthService.check(1L);

        assertTrue(dto.isHealthy());
        assertEquals("容器运行中（未配置健康检查）", dto.getMessage());
    }
}
