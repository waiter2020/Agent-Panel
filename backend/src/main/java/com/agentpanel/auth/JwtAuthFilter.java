package com.agentpanel.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PROXY_COOKIE = "AP_PROXY";
    private static final Pattern PROXY_PATH = Pattern.compile("^/api/apps/(\\d+)/proxy(?:-ws)?/([^/]+)");

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = jwtService.parseToken(token);
                if (!validateProxyClaims(request, claims)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                @SuppressWarnings("unchecked")
                List<String> permissions = claims.get("permissions", List.class);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                var authorities = new ArrayList<SimpleGrantedAuthority>();
                if (permissions != null) {
                    authorities.addAll(permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
                }
                if (roles != null) {
                    authorities.addAll(roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .toList());
                }
                Long userId = Long.parseLong(claims.getSubject());
                String username = claims.get("username", String.class);
                Long tenantId = claims.get("tenantId", Long.class);
                if (tenantId == null) {
                    tenantId = 1L;
                }
                var principal = new AuthPrincipal(userId, username, tenantId);
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean validateProxyClaims(HttpServletRequest request, Claims claims) {
        if (!"proxy".equals(claims.get("type", String.class))) {
            return true;
        }
        Matcher matcher = PROXY_PATH.matcher(request.getRequestURI());
        if (!matcher.find()) {
            return false;
        }
        Long pathAppId = Long.parseLong(matcher.group(1));
        String pathConsoleKey = matcher.group(2);
        Object claimAppId = claims.get("appId");
        String claimConsoleKey = claims.get("consoleKey", String.class);
        if (claimAppId == null || claimConsoleKey == null) {
            return false;
        }
        long tokenAppId = claimAppId instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(claimAppId));
        return tokenAppId == pathAppId && claimConsoleKey.equals(pathConsoleKey);
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
        Matcher matcher = PROXY_PATH.matcher(request.getRequestURI());
        if (matcher.find() && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (PROXY_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
