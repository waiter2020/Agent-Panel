package com.agentpanel.auth;

import com.agentpanel.common.TenantAccessHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.username();
        }
        return auth.getName();
    }

    public static boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    public static Long getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return 1L;
        }
        if (auth.getPrincipal() instanceof AuthPrincipal principal) {
            if (principal.tenantId() != null) {
                return principal.tenantId();
            }
            if (isSuperAdmin()) {
                return 1L;
            }
            return TenantAccessHelper.requireResolvableTenantId();
        }
        return 1L;
    }
}
