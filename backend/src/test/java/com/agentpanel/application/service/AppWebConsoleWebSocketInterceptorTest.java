package com.agentpanel.application.service;

import com.agentpanel.auth.JwtService;
import com.agentpanel.system.entity.SysUser;
import com.agentpanel.system.repository.SysUserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppWebConsoleWebSocketInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private SysUserRepository userRepository;
    @Mock private ServerHttpResponse response;
    @Mock private WebSocketHandler webSocketHandler;

    @InjectMocks
    private AppWebConsoleWebSocketInterceptor interceptor;

    @Test
    void beforeHandshakeAcceptsProxyPath() throws Exception {
        assertTrue(beforeHandshakeForPath("/api/apps/6/proxy/gateway"));
    }

    @Test
    void beforeHandshakeAcceptsProxyWsPath() throws Exception {
        assertTrue(beforeHandshakeForPath("/api/apps/6/proxy-ws/gateway"));
    }

    @Test
    void beforeHandshakePreservesUpstreamQueryAndStripsProxyToken() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        assertTrue(beforeHandshakeForPath("/api/apps/6/proxy-ws/gateway/socket",
                "token=proxy-token&client=control-ui", attributes));
        assertEquals("/api/apps/6/proxy-ws/gateway/socket?client=control-ui", attributes.get("requestUri"));
    }

    private boolean beforeHandshakeForPath(String path) throws Exception {
        return beforeHandshakeForPath(path, null, new HashMap<>());
    }

    private boolean beforeHandshakeForPath(String path, String queryString, Map<String, Object> attributes)
            throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("7");
        when(claims.get("type", String.class)).thenReturn("proxy");
        when(claims.get("appId")).thenReturn(6L);
        when(claims.get("consoleKey", String.class)).thenReturn("gateway");
        when(claims.get("permissions", List.class)).thenReturn(List.of("app:read"));
        when(claims.get("username", String.class)).thenReturn("panel-user");
        when(claims.get("tenantId", Long.class)).thenReturn(1L);

        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("panel-user");
        user.setTenantId(1L);

        when(jwtService.parseToken("proxy-token")).thenReturn(claims);
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest(new MockServletContext());
        servletRequest.setRequestURI(path);
        if (queryString != null) {
            servletRequest.setQueryString(queryString);
        }
        servletRequest.addHeader("Authorization", "Bearer proxy-token");
        var request = new org.springframework.http.server.ServletServerHttpRequest(servletRequest);

        boolean accepted = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);
        assertEquals(6L, attributes.get("appId"));
        assertEquals("gateway", attributes.get("portRef"));
        return accepted;
    }
}
