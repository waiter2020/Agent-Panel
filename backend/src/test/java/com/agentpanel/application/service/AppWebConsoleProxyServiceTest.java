package com.agentpanel.application.service;

import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.system.service.AuditService;
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
class AppWebConsoleProxyServiceTest {

    @Mock private ApplicationService applicationService;
    @Mock private AgentTemplateRepository templateRepository;
    @Mock private PeerUrlResolver peerUrlResolver;
    @Mock private AuditService auditService;
    @Mock private AgentRuntimeProperties runtimeProperties;
    @Mock private OpenClawTrustedProxyHeaders trustedProxyHeaders;

    @InjectMocks
    private AppWebConsoleProxyService proxyService;

    @Test
    void buildProxyPathFormatsExpectedUrl() {
        assertEquals("/api/apps/5/proxy/gateway/", proxyService.buildProxyPath(5L, "gateway"));
    }

    @Test
    void buildProxyWsPrefixFormatsExpectedUrl() {
        assertEquals("/api/apps/5/proxy-ws/gateway", proxyService.buildProxyWsPrefix(5L, "gateway"));
    }

    @Test
    void extractSubPathSupportsProxyWsPrefix() throws Exception {
        var method = AppWebConsoleProxyService.class.getDeclaredMethod(
                "extractSubPath", String.class, Long.class, String.class);
        method.setAccessible(true);
        String sub = (String) method.invoke(proxyService, "/api/apps/3/proxy-ws/gateway/chat", 3L, "gateway");
        assertEquals("/chat", sub);
    }

    @Test
    void proxyRejectsUnauthorizedPortRef() {
        Application app = runningApp();
        AgentTemplate template = templateWithConsole("gateway");

        when(applicationService.requireApplication(1L)).thenReturn(app);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        assertThrows(BusinessException.class, () -> proxyService.proxy(
                1L, "unknown-port", mock(jakarta.servlet.http.HttpServletRequest.class),
                mock(jakarta.servlet.http.HttpServletResponse.class)));
    }

    @Test
    void isSuccessfulProbeStatusAccepts2xxAnd3xxOnly() {
        assertTrue(AppWebConsoleProxyService.isSuccessfulProbeStatus(200));
        assertTrue(AppWebConsoleProxyService.isSuccessfulProbeStatus(302));
        assertFalse(AppWebConsoleProxyService.isSuccessfulProbeStatus(400));
        assertFalse(AppWebConsoleProxyService.isSuccessfulProbeStatus(500));
    }

    @Test
    void rewriteContentSecurityPolicyAllowsSelfFraming() {
        String input = "default-src 'self'; frame-ancestors 'none'; script-src 'self'";
        String rewritten = AppWebConsoleProxyService.rewriteContentSecurityPolicy(input);
        assertEquals("default-src 'self'; frame-ancestors 'self'; script-src 'self'", rewritten);
    }

    @Test
    void rewriteContentSecurityPolicyAddsSelfWhenMissing() {
        assertEquals("default-src 'self'; frame-ancestors 'self'",
                AppWebConsoleProxyService.rewriteContentSecurityPolicy("default-src 'self'"));
    }

    private Application runningApp() {
        Application app = new Application();
        app.setId(1L);
        app.setTemplateId(10L);
        app.setStatus("running");
        app.setRuntimeRef("docker:abc");
        app.setPorts(List.of(Map.of("name", "gateway", "containerPort", 18789)));
        return app;
    }

    private AgentTemplate templateWithConsole(String key) {
        AgentTemplate template = new AgentTemplate();
        template.setId(10L);
        template.setManagementSchema(Map.of(
                "webConsoles", List.of(Map.of("key", key, "title", "Console"))));
        return template;
    }
}
