package com.agentpanel.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String apiKey = resolveApiKey(request);
            if (apiKey != null) {
                var keyOpt = apiKeyService.validate(apiKey);
                if (keyOpt.isPresent()) {
                    var key = keyOpt.get();
                    String path = request.getRequestURI();
                    if (ApiKeyScopeMatcher.isApiKeyManagedPath(path)
                            && !ApiKeyScopeMatcher.hasRequiredScope(path, request.getMethod(), key.getScopes())) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getOutputStream().write(
                                "{\"code\":403,\"message\":\"API 密钥权限不足\"}".getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    var authorities = key.getScopes().stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                    var principal = new AuthPrincipal(0L, "api-key:" + key.getName(), null);
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveApiKey(HttpServletRequest request) {
        String header = request.getHeader("X-API-Key");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            if (!isJwtFormat(token)) {
                return token;
            }
        }
        return null;
    }

    private boolean isJwtFormat(String token) {
        String[] parts = token.split("\\.");
        return parts.length == 3;
    }
}
