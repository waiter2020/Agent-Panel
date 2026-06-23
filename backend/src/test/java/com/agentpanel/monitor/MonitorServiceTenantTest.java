package com.agentpanel.monitor;

import com.agentpanel.application.entity.Application;
import com.agentpanel.application.service.ApplicationService;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.RuntimeProviderFactory;
import com.agentpanel.runtime.api.LogOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTenantTest {

    @Mock private ApplicationService applicationService;
    @Mock private RuntimeProviderFactory runtimeProviderFactory;
    @Mock private AgentRuntimeProperties runtimeProperties;

    @InjectMocks
    private MonitorService monitorService;

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
}
