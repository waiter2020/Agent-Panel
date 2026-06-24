package com.agentpanel.application.service;

import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.config.OpenClawProperties;
import com.agentpanel.system.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Mock private OpenClawProperties openClawProperties;
    @Mock private OpenClawOriginResolver openClawOriginResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AppWebConsoleProxyService proxyService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        proxyService = new AppWebConsoleProxyService(
                applicationService,
                templateRepository,
                peerUrlResolver,
                auditService,
                runtimeProperties,
                trustedProxyHeaders,
                openClawProperties,
                openClawOriginResolver,
                objectMapper
        );
    }

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
    void extractSubPathPreservesQueryForProxyWsPrefix() throws Exception {
        var method = AppWebConsoleProxyService.class.getDeclaredMethod(
                "extractSubPath", String.class, Long.class, String.class);
        method.setAccessible(true);
        String sub = (String) method.invoke(proxyService,
                "/api/apps/3/proxy-ws/gateway/socket?client=control-ui", 3L, "gateway");
        assertEquals("/socket?client=control-ui", sub);
    }

    @Test
    void extractSubPathPreservesQueryForProxyWsRoot() throws Exception {
        var method = AppWebConsoleProxyService.class.getDeclaredMethod(
                "extractSubPath", String.class, Long.class, String.class);
        method.setAccessible(true);
        String sub = (String) method.invoke(proxyService,
                "/api/apps/3/proxy-ws/gateway?client=control-ui", 3L, "gateway");
        assertEquals("/?client=control-ui", sub);
    }

    @Test
    void extractSubPathSupportsProxyPrefix() throws Exception {
        var method = AppWebConsoleProxyService.class.getDeclaredMethod(
                "extractSubPath", String.class, Long.class, String.class);
        method.setAccessible(true);
        String sub = (String) method.invoke(proxyService, "/api/apps/3/proxy/gateway/chat", 3L, "gateway");
        assertEquals("/chat", sub);
    }

    @Test
    void isControlUiConfigPathMatchesExpectedFiles() {
        assertTrue(AppWebConsoleProxyService.isControlUiConfigPath("/control-ui-config.json"));
        assertTrue(AppWebConsoleProxyService.isControlUiConfigPath("/assets/control-ui-config.json"));
        assertFalse(AppWebConsoleProxyService.isControlUiConfigPath("/index.html"));
    }

    @Test
    void rewriteControlUiConfigJsonMergesPanelOrigins() throws Exception {
        when(openClawProperties.resolvePanelPublicOrigins()).thenReturn(List.of(
                "http://localhost:8080",
                "http://127.0.0.1:8080"
        ));
        byte[] input = """
                {"gateway":{"controlUi":{"allowedOrigins":["http://localhost:18789"]}}}
                """.strip().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] rewritten = proxyService.rewriteControlUiConfigJson(input, List.of("http://localhost:8080"));
        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(rewritten, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> gateway = (Map<String, Object>) root.get("gateway");
        @SuppressWarnings("unchecked")
        Map<String, Object> controlUi = (Map<String, Object>) gateway.get("controlUi");
        @SuppressWarnings("unchecked")
        List<String> origins = (List<String>) controlUi.get("allowedOrigins");
        assertTrue(origins.contains("http://localhost:18789"));
        assertTrue(origins.contains("http://localhost:8080"));
        assertTrue(origins.contains("http://127.0.0.1:8080"));
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
    void injectOpenClawTrustedProxyBootstrapClearsStorageAndSetsWsUrl() {
        String html = "<html><body><h1>Dashboard</h1></body></html>";
        String injected = AppWebConsoleProxyService.injectOpenClawTrustedProxyBootstrap(html, 6L, "gateway");
        assertTrue(injected.contains("agentpanel.gateway.bootstrap.6"));
        assertTrue(injected.contains("/api/apps/6/proxy/gateway"));
        assertTrue(injected.contains("</body>"));
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
