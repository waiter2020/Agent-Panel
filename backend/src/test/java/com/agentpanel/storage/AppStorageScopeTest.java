package com.agentpanel.storage;

import com.agentpanel.application.service.ApplicationService;
import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.common.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppStorageScopeTest {

    @Mock
    private ApplicationService applicationService;

    private AppStorageScope scope;

    @BeforeEach
    void setUp() {
        scope = new AppStorageScope(applicationService);
        setTenantAuth(7L, List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantGlobalListUsesTenantPrefix() {
        assertEquals("tenants/7/", scope.resolveGlobalListPrefix(null));
        assertEquals("tenants/7/docs/", scope.resolveGlobalListPrefix("/docs/"));
    }

    @Test
    void tenantGlobalKeyUsesTenantPrefix() {
        assertEquals("tenants/7/readme.md", scope.resolveGlobalKey("readme.md"));
        assertEquals("tenants/7/readme.md", scope.resolveDeleteKey(null, "readme.md"));
    }

    @Test
    void appScopedKeysValidateApplicationAccess() {
        assertEquals("apps/42/config.json", scope.resolveGlobalKey("apps/42/config.json"));
        verify(applicationService).requireApplication(42L);
    }

    @Test
    void tenantCannotAccessAnotherTenantPrefix() {
        assertThrows(BusinessException.class, () -> scope.resolveGlobalKey("tenants/8/secret.txt"));
        assertThrows(BusinessException.class, () -> scope.resolveGlobalListPrefix("tenants/8/"));
    }

    @Test
    void superAdminKeepsRawGlobalScope() {
        setTenantAuth(1L, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

        assertEquals("", scope.resolveGlobalListPrefix(null));
        assertEquals("shared/readme.md", scope.resolveGlobalKey("shared/readme.md"));
    }

    private void setTenantAuth(Long tenantId, List<SimpleGrantedAuthority> authorities) {
        var principal = new AuthPrincipal(1L, "user", tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
