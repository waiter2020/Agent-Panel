package com.agentpanel.common;

import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.auth.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class TenantAccessHelper {

    private TenantAccessHelper() {
    }

    /**
     * Ensures the current user may access a tenant-scoped resource.
     * Super admins bypass. Uses a generic not-found message to avoid cross-tenant ID enumeration.
     */
    public static void requireOwnedTenant(Long entityTenantId, String notFoundMessage) {
        if (SecurityUtils.isSuperAdmin()) {
            return;
        }
        Long currentTenantId = requireResolvableTenantId();
        if (entityTenantId == null || !entityTenantId.equals(currentTenantId)) {
            throw new BusinessException(notFoundMessage);
        }
    }

    public static Long requireResolvableTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BusinessException(401, "未登录");
        }
        if (auth.getPrincipal() instanceof AuthPrincipal principal) {
            if (principal.tenantId() != null) {
                return principal.tenantId();
            }
            throw new BusinessException(403, "无权限访问");
        }
        throw new BusinessException(403, "无权限访问");
    }
}
