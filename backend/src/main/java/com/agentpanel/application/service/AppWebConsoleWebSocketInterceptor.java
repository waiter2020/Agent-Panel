package com.agentpanel.application.service;

import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.auth.JwtService;
import com.agentpanel.system.entity.SysUser;
import com.agentpanel.system.repository.SysUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
public class AppWebConsoleWebSocketInterceptor implements HandshakeInterceptor {

    private static final String PROXY_COOKIE = "AP_PROXY";
    private static final Pattern PROXY_WS_PATH = Pattern.compile("/api/apps/(\\d+)/proxy(?:-ws)?/([^/]+)");

    private final JwtService jwtService;
    private final SysUserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String token = resolveToken(httpRequest);
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtService.parseToken(token);
            if (!"proxy".equals(claims.get("type", String.class))) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            Matcher matcher = PROXY_WS_PATH.matcher(request.getURI().getPath());
            if (!matcher.find()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }
            long pathAppId = Long.parseLong(matcher.group(1));
            String pathPortRef = matcher.group(2);
            Object claimAppId = claims.get("appId");
            String claimConsoleKey = claims.get("consoleKey", String.class);
            if (claimAppId == null || claimConsoleKey == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            long tokenAppId = claimAppId instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(claimAppId));
            if (tokenAppId != pathAppId || !claimConsoleKey.equals(pathPortRef)) {
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
            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);
            if (permissions == null || !permissions.contains("app:read")) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put("appId", pathAppId);
            attributes.put("portRef", pathPortRef);
            attributes.put("requestUri", buildProxyRequestUri(request.getURI().getPath(), request.getURI().getRawQuery()));
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
            if (permissions != null) {
                authorities.addAll(permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
            }
            var principal = new AuthPrincipal(userId, username, tenantId);
            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            attributes.put("authentication", auth);
            attributes.put("forwardedHost", httpRequest.getHeader("Host"));
            String forwardedProto = httpRequest.getHeader("X-Forwarded-Proto");
            if (forwardedProto == null || forwardedProto.isBlank()) {
                forwardedProto = httpRequest.isSecure() ? "https" : "http";
            }
            attributes.put("forwardedProto", forwardedProto);
            attributes.put("referer", httpRequest.getHeader("Referer"));
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

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (PROXY_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String buildProxyRequestUri(String path, String query) {
        String sanitizedQuery = sanitizeQuery(query);
        return path + (sanitizedQuery.isBlank() ? "" : "?" + sanitizedQuery);
    }

    private String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            if ("token".equalsIgnoreCase(key)) {
                continue;
            }
            parts.add(pair);
        }
        return String.join("&", parts);
    }
}
