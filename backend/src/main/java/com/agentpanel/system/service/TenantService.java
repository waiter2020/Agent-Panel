package com.agentpanel.system.service;

import com.agentpanel.application.service.KanbanBoardInitializer;
import com.agentpanel.common.BusinessException;
import com.agentpanel.system.dto.TenantDto;
import com.agentpanel.system.entity.SysTenant;
import com.agentpanel.system.repository.SysTenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final SysTenantRepository tenantRepository;
    private final KanbanBoardInitializer kanbanBoardInitializer;

    public List<TenantDto> list() {
        return tenantRepository.findAll().stream().map(this::toDto).toList();
    }

    public TenantDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public TenantDto create(TenantDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("租户名称不能为空");
        }
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new BusinessException("租户编码不能为空");
        }
        String code = dto.getCode().trim();
        if (tenantRepository.existsByCode(code)) {
            throw new BusinessException("租户编码已存在");
        }
        SysTenant tenant = new SysTenant();
        tenant.setName(dto.getName().trim());
        tenant.setCode(code);
        SysTenant saved = tenantRepository.save(tenant);
        kanbanBoardInitializer.ensureDefaultBoard(saved.getId());
        return toDto(saved);
    }

    @Transactional
    public TenantDto update(Long id, TenantDto dto) {
        SysTenant tenant = find(id);
        if (id == 1L && dto.getCode() != null && !"default".equals(dto.getCode().trim())) {
            throw new BusinessException("默认租户编码不可修改");
        }
        if (dto.getName() != null && !dto.getName().isBlank()) {
            tenant.setName(dto.getName().trim());
        }
        if (dto.getCode() != null && !dto.getCode().isBlank() && !dto.getCode().trim().equals(tenant.getCode())) {
            String code = dto.getCode().trim();
            if (tenantRepository.existsByCode(code)) {
                throw new BusinessException("租户编码已存在");
            }
            tenant.setCode(code);
        }
        return toDto(tenantRepository.save(tenant));
    }

    @Transactional
    public void delete(Long id) {
        if (id == 1L) {
            throw new BusinessException("默认租户不可删除");
        }
        tenantRepository.delete(find(id));
    }

    private SysTenant find(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException("租户不存在"));
    }

    private TenantDto toDto(SysTenant tenant) {
        TenantDto dto = new TenantDto();
        dto.setId(tenant.getId());
        dto.setName(tenant.getName());
        dto.setCode(tenant.getCode());
        dto.setCreatedAt(tenant.getCreatedAt());
        dto.setUpdatedAt(tenant.getUpdatedAt());
        return dto;
    }
}
