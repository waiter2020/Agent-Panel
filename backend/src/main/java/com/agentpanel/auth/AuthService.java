package com.agentpanel.auth;

import com.agentpanel.auth.dto.CurrentUserResponse;
import com.agentpanel.auth.dto.LoginRequest;
import com.agentpanel.auth.dto.LoginResponse;
import com.agentpanel.common.BusinessException;
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
        List<SysMenu> menus = menuRepository.findByDeletedFalseOrderByOrderNoAsc();
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                roles, permissions, buildMenuTree(menus, null));
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

    private List<CurrentUserResponse.MenuNode> buildMenuTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .map(m -> new CurrentUserResponse.MenuNode(
                        m.getId(), m.getName(), m.getPath(), m.getIcon(), m.getComponent(),
                        buildMenuTree(menus, m.getId())))
                .toList();
    }
}
