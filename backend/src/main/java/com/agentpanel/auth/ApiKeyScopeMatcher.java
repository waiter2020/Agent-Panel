package com.agentpanel.auth;

import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Set;

/**
 * Maps API-key-authenticated request paths to required scope authorities.
 * JWT users bypass this check (handled by RBAC {@code @PreAuthorize}).
 */
public final class ApiKeyScopeMatcher {

    private static final Set<String> API_KEY_PATH_PREFIXES = Set.of(
            "/v1/", "/api/memory", "/api/skills", "/api/delegations");

    private ApiKeyScopeMatcher() {
    }

    public static boolean isApiKeyManagedPath(String path) {
        if (path == null) {
            return false;
        }
        return API_KEY_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    public static boolean hasRequiredScope(String path, String method, List<String> scopes) {
        String required = resolveRequiredScope(path, method);
        if (required == null) {
            return false;
        }
        return scopes != null && scopes.contains(required);
    }

    static String resolveRequiredScope(String path, String method) {
        if (path == null || method == null) {
            return null;
        }
        String httpMethod = method.toUpperCase();
        if (path.startsWith("/v1/")) {
            return "ai:chat";
        }
        if (path.startsWith("/api/memory")) {
            if (HttpMethod.GET.matches(httpMethod)) {
                return "memory:read";
            }
            if (HttpMethod.POST.matches(httpMethod) || HttpMethod.DELETE.matches(httpMethod)) {
                return "memory:write";
            }
            return null;
        }
        if (path.startsWith("/api/skills")) {
            if (HttpMethod.GET.matches(httpMethod)) {
                return "skill:read";
            }
            if (HttpMethod.POST.matches(httpMethod) || HttpMethod.PUT.matches(httpMethod)
                    || HttpMethod.DELETE.matches(httpMethod)) {
                return "skill:write";
            }
            return null;
        }
        if (path.startsWith("/api/delegations")) {
            if (path.endsWith("/webhook")) {
                return "delegation:write";
            }
            if (HttpMethod.GET.matches(httpMethod)) {
                return "delegation:read";
            }
            if (HttpMethod.POST.matches(httpMethod) || HttpMethod.PATCH.matches(httpMethod)) {
                return "delegation:write";
            }
            return null;
        }
        return null;
    }
}
