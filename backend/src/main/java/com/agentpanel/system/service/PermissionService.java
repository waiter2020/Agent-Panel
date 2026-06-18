package com.agentpanel.system.service;

import com.agentpanel.common.BusinessException;
import com.agentpanel.common.PageResult;
import com.agentpanel.system.dto.PermissionDto;
import com.agentpanel.system.entity.SysPermission;
import com.agentpanel.system.repository.SysPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysPermissionRepository permissionRepository;

    public List<PermissionDto> list() {
        return permissionRepository.findByDeletedFalse().stream().map(this::toDto).toList();
    }

    @Transactional
    public PermissionDto create(PermissionDto dto) {
        SysPermission permission = new SysPermission();
        permission.setCode(dto.getCode());
        permission.setName(dto.getName());
        permission.setType(dto.getType() != null ? dto.getType() : "api");
        permission.setParentId(dto.getParentId());
        return toDto(permissionRepository.save(permission));
    }

    @Transactional
    public PermissionDto update(Long id, PermissionDto dto) {
        SysPermission permission = permissionRepository.findById(id).filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException("权限不存在"));
        permission.setName(dto.getName());
        permission.setType(dto.getType());
        permission.setParentId(dto.getParentId());
        return toDto(permissionRepository.save(permission));
    }

    @Transactional
    public void delete(Long id) {
        SysPermission permission = permissionRepository.findById(id).filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException("权限不存在"));
        permission.setDeleted(true);
        permissionRepository.save(permission);
    }

    private PermissionDto toDto(SysPermission permission) {
        PermissionDto dto = new PermissionDto();
        dto.setId(permission.getId());
        dto.setCode(permission.getCode());
        dto.setName(permission.getName());
        dto.setType(permission.getType());
        dto.setParentId(permission.getParentId());
        return dto;
    }
}
