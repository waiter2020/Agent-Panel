package com.agentpanel.terminal;

import com.agentpanel.auth.AuthPrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TerminalWebSocketInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private SysUserRepository userRepository;
    @Mock private ServerHttpResponse response;
    @Mock private WebSocketHandler webSocketHandler;

    @InjectMocks
    private TerminalWebSocketInterceptor interceptor;

    @Test
    void beforeHandshakeSetsAuthPrincipalWithTenantId() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("7");
        when(claims.get("permissions", List.class)).thenReturn(List.of("app:terminal"));
        when(claims.get("username", String.class)).thenReturn("tenant-user");
        when(claims.get("tenantId", Long.class)).thenReturn(3L);

        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("tenant-user");
        user.setTenantId(3L);

        when(jwtService.parseToken("ticket")).thenReturn(claims);
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest(new MockServletContext());
        servletRequest.setRequestURI("/api/apps/42/terminal/ws");
        servletRequest.setParameter("token", "ticket");
        var request = new org.springframework.http.server.ServletServerHttpRequest(servletRequest);

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        assertTrue(accepted);
        var auth = (UsernamePasswordAuthenticationToken) attributes.get("authentication");
        assertNotNull(auth);
        assertInstanceOf(AuthPrincipal.class, auth.getPrincipal());
        AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
        assertEquals(7L, principal.userId());
        assertEquals("tenant-user", principal.username());
        assertEquals(3L, principal.tenantId());
    }

    @Test
    void authPrincipalAllowsTenantAccessHelperResolution() {
        var principal = new AuthPrincipal(7L, "tenant-user", 3L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertDoesNotThrow(() -> com.agentpanel.common.TenantAccessHelper.requireOwnedTenant(3L, "应用不存在"));
        assertThrows(com.agentpanel.common.BusinessException.class,
                () -> com.agentpanel.common.TenantAccessHelper.requireOwnedTenant(1L, "应用不存在"));

        SecurityContextHolder.clearContext();
    }
}
