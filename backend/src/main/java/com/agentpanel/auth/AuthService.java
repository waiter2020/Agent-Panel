package com.agentpanel.auth;

import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.auth.dto.CurrentUserResponse;
import com.agentpanel.auth.dto.LoginRequest;
import com.agentpanel.auth.dto.LoginResponse;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.TenantAccessHelper;
import com.agentpanel.system.entity.SysMenu;
import com.agentpanel.system.entity.SysRefreshToken;
import com.agentpanel.system.entity.SysUser;
import com.agentpanel.system.repository.SysMenuRepository;
import com.agentpanel.system.repository.SysRefreshTokenRepository;
import com.agentpanel.system.repository.SysUserRepository;
import com.agentpanel.system.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SysUserRepository userRepository;
    private final SysMenuRepository menuRepository;
    private final SysRefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final ApplicationRepository applicationRepository;
    private final AgentTemplateRepository agentTemplateRepository;

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        SysUser user = userRepository.findByUsernameAndDeletedFalse(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        List<String> roles = user.getRoles().stream().map(r -> r.getCode()).toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .toList();
        String accessToken = jwtService.generateAccessToken(user, roles, permissions);
        String refreshToken = jwtService.generateRefreshToken();
        SysRefreshToken tokenEntity = new SysRefreshToken();
        tokenEntity.setUserId(user.getId());
        tokenEntity.setToken(refreshToken);
        tokenEntity.setExpiresAt(Instant.now().plus(jwtService.getRefreshTtl()));
        refreshTokenRepository.save(tokenEntity);
        auditService.log(user.getId(), user.getUsername(), "login", "user", String.valueOf(user.getId()), null,
                httpRequest.getRemoteAddr(), "success");
        return new LoginResponse(accessToken, refreshToken,
                new LoginResponse.UserInfo(user.getId(), user.getUsername(), user.getNickname(), roles));
    }

    public Map<String, String> refresh(String refreshToken) {
        SysRefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> new BusinessException(401, "RefreshToken 无效"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(401, "RefreshToken 已过期");
        }
        SysUser user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        List<String> roles = user.getRoles().stream().map(r -> r.getCode()).toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .toList();
        return Map.of("accessToken", jwtService.generateAccessToken(user, roles, permissions));
    }

    public CurrentUserResponse currentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        SysUser user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        List<String> roles = user.getRoles().stream().map(r -> r.getCode()).toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .toList();
        Set<Long> permissionIds = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getId())
                .collect(Collectors.toSet());
        List<SysMenu> menus = menuRepository.findByDeletedFalseOrderByOrderNoAsc();
        List<SysMenu> visibleMenus = menus.stream()
                .filter(menu -> !menu.isHidden())
                .filter(menu -> menu.getPermissionId() == null || permissionIds.contains(menu.getPermissionId()))
                .toList();
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                roles, permissions, buildMenuTree(visibleMenus, null));
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            auditService.log(userId, SecurityUtils.getCurrentUsername(), "logout", "user",
                    String.valueOf(userId), null, null, "success");
        }
    }

    public Map<String, String> createSseTicket() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        SysUser user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .toList();
        return Map.of("token", jwtService.generateSseToken(user, permissions));
    }

    public String createProxySession(Long appId, String consoleKey) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (appId == null || consoleKey == null || consoleKey.isBlank()) {
            throw new BusinessException("appId 与 consoleKey 不能为空");
        }
        SysUser user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        List<String> roles = user.getRoles().stream().map(r -> r.getCode()).toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .toList();
        if (!permissions.contains("app:read")) {
            throw new BusinessException(403, "无应用查看权限");
        }
        Application app = applicationRepository.findByIdAndDeletedFalse(appId)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        TenantAccessHelper.requireOwnedTenant(app.getTenantId(), "应用不存在");
        validateProxyConsoleKey(app, consoleKey.trim());
        return jwtService.generateProxyToken(user, roles, permissions, appId, app.getTenantId(), consoleKey.trim());
    }

    @SuppressWarnings("unchecked")
    private void validateProxyConsoleKey(Application app, String consoleKey) {
        AgentTemplate template = agentTemplateRepository.findById(app.getTemplateId())
                .orElseThrow(() -> new BusinessException("模板不存在"));
        Map<String, Object> schema = template.getManagementSchema();
        if (schema == null || schema.isEmpty()) {
            throw new BusinessException("模板未配置 Web 控制台");
        }
        List<Map<String, Object>> consoles = (List<Map<String, Object>>) schema.get("webConsoles");
        if (consoles == null) {
            throw new BusinessException("未授权的代理端口: " + consoleKey);
        }
        boolean allowed = consoles.stream()
                .anyMatch(c -> consoleKey.equals(String.valueOf(c.get("key")))
                        || consoleKey.equals(String.valueOf(c.get("portRef"))));
        if (!allowed) {
            throw new BusinessException("未授权的代理端口: " + consoleKey);
        }
    }

    private List<CurrentUserResponse.MenuNode> buildMenuTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .map(m -> {
                    List<CurrentUserResponse.MenuNode> children = buildMenuTree(menus, m.getId());
                    if (m.getComponent() == null && children.isEmpty()) {
                        return null;
                    }
                    return new CurrentUserResponse.MenuNode(
                            m.getId(), m.getName(), m.getPath(), m.getIcon(), m.getComponent(), children);
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
