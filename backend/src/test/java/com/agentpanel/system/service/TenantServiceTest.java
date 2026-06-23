package com.agentpanel.system.service;

import com.agentpanel.application.service.KanbanBoardInitializer;
import com.agentpanel.common.BusinessException;
import com.agentpanel.system.dto.TenantDto;
import com.agentpanel.system.entity.SysTenant;
import com.agentpanel.system.repository.SysTenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private SysTenantRepository tenantRepository;

    @Mock
    private KanbanBoardInitializer kanbanBoardInitializer;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void createPersistsTenant() {
        when(tenantRepository.existsByCode("acme")).thenReturn(false);
        when(tenantRepository.save(any(SysTenant.class))).thenAnswer(invocation -> {
            SysTenant tenant = invocation.getArgument(0);
            tenant.setId(2L);
            return tenant;
        });

        TenantDto request = new TenantDto();
        request.setName("Acme 租户");
        request.setCode("acme");

        TenantDto created = tenantService.create(request);

        assertEquals(2L, created.getId());
        assertEquals("Acme 租户", created.getName());
        assertEquals("acme", created.getCode());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(tenantRepository.existsByCode("default")).thenReturn(true);

        TenantDto request = new TenantDto();
        request.setName("重复");
        request.setCode("default");

        assertThrows(BusinessException.class, () -> tenantService.create(request));
    }

    @Test
    void deleteRejectsDefaultTenant() {
        assertThrows(BusinessException.class, () -> tenantService.delete(1L));
        verify(tenantRepository, never()).delete(any());
    }

    @Test
    void updateRejectsChangingDefaultTenantCode() {
        SysTenant tenant = new SysTenant();
        tenant.setId(1L);
        tenant.setName("默认租户");
        tenant.setCode("default");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        TenantDto request = new TenantDto();
        request.setCode("renamed");

        assertThrows(BusinessException.class, () -> tenantService.update(1L, request));
    }
}
