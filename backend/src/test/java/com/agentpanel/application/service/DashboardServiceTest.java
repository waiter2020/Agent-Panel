package com.agentpanel.application.service;

import com.agentpanel.application.dto.DashboardStatsDto;
import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.application.repository.AgentTopologyRepository;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.config.AgentRuntimeProperties;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private AgentTemplateRepository templateRepository;
    @Mock private AgentTopologyRepository topologyRepository;
    @Mock private AgentRuntimeProperties runtimeProperties;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        var principal = new AuthPrincipal(1L, "user", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(runtimeProperties.getProvider()).thenReturn("docker");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getStatsFiltersByTenantForRegularUser() {
        Application running = app(1L, "a1", "running", 1L);
        Application stopped = app(2L, "a2", "stopped", 1L);
        AgentTemplate template = new AgentTemplate();
        template.setId(1L);
        template.setCode("openclaw");

        when(applicationRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L))
                .thenReturn(List.of(running, stopped));
        when(templateRepository.findByDeletedFalse()).thenReturn(List.of(template));
        when(topologyRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L)).thenReturn(List.of());

        DashboardStatsDto stats = dashboardService.getStats();

        assertEquals(2, stats.getTotalApps());
        assertEquals(1, stats.getRunningApps());
        assertEquals(1, stats.getStoppedApps());
        verify(applicationRepository).findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L);
        verify(applicationRepository, never()).findByDeletedFalseOrderByUpdatedAtDesc();
    }

    @Test
    void getStatsCountsPortConflicts() {
        Application app1 = app(1L, "a1", "running", 1L);
        app1.setPorts(List.of(Map.of("name", "http", "containerPort", 80, "hostPort", 8080, "expose", true)));
        Application app2 = app(2L, "a2", "running", 1L);
        app2.setPorts(List.of(Map.of("name", "http", "containerPort", 80, "hostPort", 8080, "expose", true)));

        AgentRuntimeProperties.Docker docker = new AgentRuntimeProperties.Docker();
        docker.setAccessHost("localhost");

        when(applicationRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L))
                .thenReturn(List.of(app1, app2));
        when(templateRepository.findByDeletedFalse()).thenReturn(List.of());
        when(topologyRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L)).thenReturn(List.of());
        when(runtimeProperties.getDocker()).thenReturn(docker);

        DashboardStatsDto stats = dashboardService.getStats();

        assertEquals(1, stats.getPortConflicts());
    }

    @Test
    void getStatsCountsDeployingApps() {
        Application deploying = app(3L, "a3", "deploying", 1L);
        when(applicationRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L))
                .thenReturn(List.of(deploying));
        when(templateRepository.findByDeletedFalse()).thenReturn(List.of());
        when(topologyRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L)).thenReturn(List.of());

        DashboardStatsDto stats = dashboardService.getStats();

        assertEquals(1, stats.getDeployingApps());
    }

    private Application app(Long id, String name, String status, Long templateId) {
        Application app = new Application();
        app.setId(id);
        app.setName(name);
        app.setStatus(status);
        app.setTemplateId(templateId);
        app.setTenantId(2L);
        app.setPorts(List.of());
        return app;
    }
}
