package com.agentpanel.monitor;

import com.agentpanel.application.entity.Application;
import com.agentpanel.application.service.ApplicationService;
import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.RuntimeProviderFactory;
import com.agentpanel.runtime.api.LogOptions;
import com.agentpanel.runtime.api.ResourceStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTenantTest {

    @Mock private ApplicationService applicationService;
    @Mock private RuntimeProviderFactory runtimeProviderFactory;
    @Mock private AgentRuntimeProperties runtimeProperties;
    @Spy private StatsRateTracker statsRateTracker = new StatsRateTracker();

    @InjectMocks
    private MonitorService monitorService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsTenantUser(long tenantId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(1L, "user", tenantId),
                        null,
                        List.of()));
    }

    @Test
    void logsStreamRejectsCrossTenantApp() {
        when(applicationService.requireApplication(99L))
                .thenThrow(new BusinessException("应用不存在"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> monitorService.logsStream(99L, new LogOptions(false, 100, null)).blockFirst());
        assertEquals("应用不存在", ex.getMessage());
    }

    @Test
    void logsStreamUsesTenantScopedApplicationLookup() {
        Application app = new Application();
        app.setId(5L);
        app.setRuntimeRef(null);
        when(applicationService.requireApplication(5L)).thenReturn(app);

        String first = monitorService.logsStream(5L, new LogOptions(false, 100, null)).blockFirst();

        assertEquals("应用尚未部署", first);
        verify(applicationService).requireApplication(5L);
    }

    @Test
    void downloadLogsRejectsCrossTenantApp() {
        when(applicationService.requireApplication(99L))
                .thenThrow(new BusinessException("应用不存在"));

        assertThrows(BusinessException.class, () -> monitorService.downloadLogs(99L, 100));
    }

    @Test
    void statsStreamPreservesAuthenticationOnIntervalThread() {
        authenticateAsTenantUser(1L);
        Application app = new Application();
        app.setId(5L);
        app.setTenantId(1L);
        app.setRuntimeRef("container-1");
        when(applicationService.requireApplication(5L)).thenReturn(app);
        when(applicationService.getStats(5L))
                .thenReturn(ResourceStats.ok(12.5, 1024, 2048, 0, 0, true));

        var flux = monitorService.statsStream(5L);
        SecurityContextHolder.clearContext();

        ResourceStats first = flux.blockFirst(Duration.ofSeconds(2));

        assertNotNull(first);
        assertTrue(first.available());
        verify(applicationService).requireApplication(5L);
        verify(applicationService).getStats(5L);
    }
}
