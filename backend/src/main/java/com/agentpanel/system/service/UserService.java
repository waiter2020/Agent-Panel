package com.agentpanel.system.service;

import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.PageResult;
import com.agentpanel.system.dto.UserDto;
import com.agentpanel.system.entity.SysRole;
import com.agentpanel.system.entity.SysUser;
import com.agentpanel.system.repository.SysRoleRepository;
import com.agentpanel.system.repository.SysTenantRepository;
import com.agentpanel.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysTenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserDto> list(int page, int pageSize) {
        Page<SysUser> result = SecurityUtils.isSuperAdmin()
                ? userRepository.findAll(PageRequest.of(page - 1, pageSize))
                : userRepository.findByDeletedFalseAndTenantId(
                        SecurityUtils.getCurrentTenantId(), PageRequest.of(page - 1, pageSize));
        List<UserDto> list = result.getContent().stream().map(this::toDto).toList();
        return new PageResult<>(list, result.getTotalElements(), page, pageSize);
    }

    public UserDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public UserDto create(UserDto dto) {
        if (userRepository.existsByUsernameAndDeletedFalse(dto.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword() != null ? dto.getPassword() : "123456"));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setStatus(dto.getStatus() != null ? dto.getStatus() : "enabled");
        user.setTenantId(resolveTenantIdForWrite(dto.getTenantId()));
        applyRoles(user, dto.getRoleIds());
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto update(Long id, UserDto dto) {
        SysUser user = find(id);
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        if (dto.getTenantId() != null && SecurityUtils.isSuperAdmin()) {
            ensureTenantExists(dto.getTenantId());
            user.setTenantId(dto.getTenantId());
        }
        if (dto.getRoleIds() != null) {
            applyRoles(user, dto.getRoleIds());
        }
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        SysUser user = find(id);
        user.setDeleted(true);
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        SysUser user = find(id);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
    }

    private Long resolveTenantIdForWrite(Long requestedTenantId) {
        if (SecurityUtils.isSuperAdmin()) {
            Long tenantId = requestedTenantId != null ? requestedTenantId : 1L;
            ensureTenantExists(tenantId);
            return tenantId;
        }
        return SecurityUtils.getCurrentTenantId();
    }

    private void ensureTenantExists(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new BusinessException("租户不存在");
        }
    }

    private void applyRoles(SysUser user, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        List<SysRole> roles = roleRepository.findAllById(roleIds);
        user.setRoles(new HashSet<>(roles));
    }

    private SysUser find(Long id) {
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!SecurityUtils.isSuperAdmin()
                && !SecurityUtils.getCurrentTenantId().equals(user.getTenantId())) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private UserDto toDto(SysUser user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setStatus(user.getStatus());
        dto.setTenantId(user.getTenantId());
        tenantRepository.findById(user.getTenantId()).ifPresent(t -> dto.setTenantName(t.getName()));
        dto.setRoleIds(user.getRoles().stream().map(SysRole::getId).toList());
        return dto;
    }
}
