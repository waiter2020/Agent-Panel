package com.agentpanel.terminal;

import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.auth.JwtService;
import com.agentpanel.system.entity.SysUser;
import com.agentpanel.system.repository.SysUserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TerminalWebSocketInterceptor implements HandshakeInterceptor {

    private static final Pattern APP_ID_PATTERN = Pattern.compile("/api/apps/(\\d+)/terminal/ws");

    private final JwtService jwtService;
    private final SysUserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtService.parseToken(token);
            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);
            if (permissions == null || !permissions.contains("app:terminal")) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            Long userId = Long.parseLong(claims.getSubject());
            SysUser user = userRepository.findById(userId)
                    .filter(u -> !u.isDeleted())
                    .orElse(null);
            if (user == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            Matcher matcher = APP_ID_PATTERN.matcher(request.getURI().getPath());
            if (!matcher.find()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }
            attributes.put("appId", Long.parseLong(matcher.group(1)));
            attributes.put("userId", userId);
            String username = claims.get("username", String.class);
            if (username == null || username.isBlank()) {
                username = user.getUsername();
            }
            attributes.put("username", username);
            Long tenantId = claims.get("tenantId", Long.class);
            if (tenantId == null) {
                tenantId = user.getTenantId() != null ? user.getTenantId() : 1L;
            }
            var authorities = new ArrayList<SimpleGrantedAuthority>();
            authorities.addAll(permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
            var principal = new AuthPrincipal(userId, username, tenantId);
            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            attributes.put("authentication", auth);
            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
