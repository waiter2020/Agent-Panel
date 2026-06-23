package com.agentpanel.terminal;

import com.agentpanel.application.service.ApplicationService;
import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.RuntimeProviderFactory;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppTerminalServiceTenantTest {

    @Mock private ApplicationService applicationService;
    @Mock private RuntimeProviderFactory runtimeProviderFactory;
    @Mock private AgentRuntimeProperties runtimeProperties;
    @Mock private AuditService auditService;

    @InjectMocks
    private AppTerminalService appTerminalService;

    @BeforeEach
    void setUp() {
        when(runtimeProperties.isTerminalEnabled()).thenReturn(true);
        var principal = new AuthPrincipal(1L, "tenant-user", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void openRejectsCrossTenantApp() {
        when(applicationService.requireApplication(10L))
                .thenThrow(new BusinessException("应用不存在"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appTerminalService.open(10L, new TerminalOutputHandler() {
                    @Override
                    public void onOutput(byte[] data) {
                    }

                    @Override
                    public void onError(String message) {
                    }

                    @Override
                    public void onClosed() {
                    }
                }));
        assertEquals("应用不存在", ex.getMessage());
    }
}
