package com.agentpanel.common;

import com.agentpanel.auth.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TenantAccessHelperTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireOwnedTenantAllowsMatchingTenant() {
        setPrincipal(1L, List.of());
        assertDoesNotThrow(() -> TenantAccessHelper.requireOwnedTenant(1L, "不存在"));
    }

    @Test
    void requireOwnedTenantRejectsCrossTenant() {
        setPrincipal(2L, List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TenantAccessHelper.requireOwnedTenant(1L, "资源不存在"));
        assertEquals("资源不存在", ex.getMessage());
    }

    @Test
    void requireOwnedTenantBypassesForSuperAdmin() {
        setPrincipal(2L, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        assertDoesNotThrow(() -> TenantAccessHelper.requireOwnedTenant(99L, "不存在"));
    }

    private void setPrincipal(Long tenantId, List<SimpleGrantedAuthority> authorities) {
        var principal = new AuthPrincipal(1L, "user", tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
