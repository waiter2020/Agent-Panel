package com.agentpanel.system.service;

import com.agentpanel.common.BusinessException;
import com.agentpanel.system.dto.RoleDto;
import com.agentpanel.system.entity.SysPermission;
import com.agentpanel.system.entity.SysRole;
import com.agentpanel.system.repository.SysPermissionRepository;
import com.agentpanel.system.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;

    public List<RoleDto> list() {
        return roleRepository.findByDeletedFalse().stream().map(this::toDto).toList();
    }

    public RoleDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public RoleDto create(RoleDto dto) {
        SysRole role = new SysRole();
        role.setCode(dto.getCode());
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus() != null ? dto.getStatus() : "enabled");
        applyPermissions(role, dto.getPermissionIds());
        return toDto(roleRepository.save(role));
    }

    @Transactional
    public RoleDto update(Long id, RoleDto dto) {
        SysRole role = find(id);
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        if (dto.getStatus() != null) {
            role.setStatus(dto.getStatus());
        }
        if (dto.getPermissionIds() != null) {
            applyPermissions(role, dto.getPermissionIds());
        }
        return toDto(roleRepository.save(role));
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = find(id);
        role.setDeleted(true);
        roleRepository.save(role);
    }

    private void applyPermissions(SysRole role, List<Long> permissionIds) {
        if (permissionIds == null) {
            return;
        }
        List<SysPermission> permissions = permissionRepository.findAllById(permissionIds);
        role.setPermissions(new HashSet<>(permissions));
    }

    private SysRole find(Long id) {
        return roleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }

    private RoleDto toDto(SysRole role) {
        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setStatus(role.getStatus());
        dto.setPermissionIds(role.getPermissions().stream().map(SysPermission::getId).toList());
        return dto;
    }
}
