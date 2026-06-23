package com.agentpanel.storage;

import com.agentpanel.application.service.ApplicationService;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppStorageScope {

    private static final String APP_PREFIX = "apps/";
    private static final String TENANT_PREFIX = "tenants/";

    private final ApplicationService applicationService;

    public String appPrefix(Long appId) {
        requireApp(appId);
        return appPrefixUnchecked(appId);
    }

    public String resolveListPrefix(Long appId, String prefix) {
        String base = appPrefix(appId);
        if (prefix == null || prefix.isBlank()) {
            return base;
        }
        return base + normalizeRelative(prefix);
    }

    public String resolveGlobalListPrefix(String prefix) {
        String normalized = normalizeOptional(prefix);
        if (SecurityUtils.isSuperAdmin()) {
            return normalized;
        }
        if (normalized.isBlank()) {
            return tenantPrefix(SecurityUtils.getCurrentTenantId());
        }
        if (normalized.startsWith(APP_PREFIX)) {
            Long appId = parseAppIdFromKey(normalized);
            if (appId == null) {
                throw new BusinessException("无效的应用文件路径");
            }
            ensureKeyInAppScope(appId, normalized);
            return normalized;
        }
        if (normalized.startsWith(TENANT_PREFIX)) {
            ensureKeyInTenantScope(normalized);
            return normalized;
        }
        return tenantPrefix(SecurityUtils.getCurrentTenantId()) + normalized;
    }

    public String resolveKey(Long appId, String key) {
        String base = appPrefix(appId);
        if (key == null || key.isBlank()) {
            throw new BusinessException("文件 key 不能为空");
        }
        String normalized = normalizeRelative(key);
        if (normalized.startsWith(APP_PREFIX)) {
            if (!normalized.startsWith(base)) {
                throw new BusinessException("无权访问该应用的文件");
            }
            return normalized;
        }
        return base + normalized;
    }

    public String resolveGlobalKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException("文件 key 不能为空");
        }
        String normalized = normalizeRelative(key);
        if (SecurityUtils.isSuperAdmin()) {
            return normalized;
        }
        if (normalized.startsWith(APP_PREFIX)) {
            Long appId = parseAppIdFromKey(normalized);
            if (appId == null) {
                throw new BusinessException("无效的应用文件路径");
            }
            ensureKeyInAppScope(appId, normalized);
            return normalized;
        }
        if (normalized.startsWith(TENANT_PREFIX)) {
            ensureKeyInTenantScope(normalized);
            return normalized;
        }
        return tenantPrefix(SecurityUtils.getCurrentTenantId()) + normalized;
    }

    public void ensureKeyInAppScope(Long appId, String key) {
        requireApp(appId);
        if (key == null || !key.startsWith(appPrefixUnchecked(appId))) {
            throw new BusinessException("无权访问该应用的文件");
        }
    }

    /** Resolves delete scope: apps/ keys must match their embedded appId; optional appId param must agree. */
    public String resolveDeleteKey(Long appId, String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException("文件 key 不能为空");
        }
        if (appId != null) {
            return resolveKey(appId, key);
        }
        String normalized = normalizeRelative(key);
        if (SecurityUtils.isSuperAdmin()) {
            return normalized;
        }
        if (normalized.startsWith(APP_PREFIX)) {
            Long scopedAppId = parseAppIdFromKey(normalized);
            if (scopedAppId == null) {
                throw new BusinessException("无效的应用文件路径");
            }
            ensureKeyInAppScope(scopedAppId, normalized);
            return normalized;
        }
        if (normalized.startsWith(TENANT_PREFIX)) {
            ensureKeyInTenantScope(normalized);
            return normalized;
        }
        return tenantPrefix(SecurityUtils.getCurrentTenantId()) + normalized;
    }

    public Long parseAppIdFromKey(String key) {
        if (key == null || !key.startsWith(APP_PREFIX)) {
            return null;
        }
        int end = key.indexOf('/', APP_PREFIX.length());
        if (end < 0) {
            return null;
        }
        try {
            return Long.parseLong(key.substring(APP_PREFIX.length(), end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void requireApp(Long appId) {
        if (appId == null) {
            throw new BusinessException("appId 不能为空");
        }
        applicationService.requireApplication(appId);
    }

    private String appPrefixUnchecked(Long appId) {
        return APP_PREFIX + appId + "/";
    }

    private void ensureKeyInTenantScope(String key) {
        String expectedPrefix = tenantPrefix(SecurityUtils.getCurrentTenantId());
        if (!key.startsWith(expectedPrefix)) {
            throw new BusinessException("无权访问该租户的文件");
        }
    }

    private String tenantPrefix(Long tenantId) {
        return TENANT_PREFIX + tenantId + "/";
    }

    private String normalizeOptional(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return normalizeRelative(path);
    }

    private String normalizeRelative(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new BusinessException("非法路径");
        }
        return normalized;
    }
}
