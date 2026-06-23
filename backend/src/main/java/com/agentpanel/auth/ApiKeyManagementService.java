package com.agentpanel.auth;

import com.agentpanel.auth.dto.ApiKeyDto;
import com.agentpanel.auth.dto.CreateApiKeyRequest;
import com.agentpanel.auth.dto.CreateApiKeyResponse;
import com.agentpanel.auth.dto.UpdateApiKeyRequest;
import com.agentpanel.auth.entity.ApiKey;
import com.agentpanel.auth.repository.ApiKeyRepository;
import com.agentpanel.config.AgentPanelProperties;
import com.agentpanel.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApiKeyManagementService {

    private static final String KEY_PREFIX = "apk_";
    private static final int KEY_RANDOM_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> VALID_SCOPES = Set.of(
            "ai:chat",
            "memory:read", "memory:write",
            "skill:read", "skill:write",
            "delegation:read", "delegation:write");

    private final ApiKeyRepository apiKeyRepository;
    private final AgentPanelProperties panelProperties;

    public List<ApiKeyDto> list() {
        List<ApiKey> keys = SecurityUtils.isSuperAdmin()
                ? apiKeyRepository.findAllByOrderByCreatedAtDesc()
                : apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(SecurityUtils.getCurrentTenantId());
        return keys.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CreateApiKeyResponse create(CreateApiKeyRequest request) {
        return createForTenant(request, SecurityUtils.getCurrentTenantId());
    }

    @Transactional
    public CreateApiKeyResponse createForTenant(CreateApiKeyRequest request, Long tenantId) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("密钥名称不能为空");
        }
        String rawKey = generateRawKey();
        ApiKey entity = new ApiKey();
        entity.setName(request.getName().trim());
        entity.setKeyPrefix(rawKey.substring(0, Math.min(16, rawKey.length())));
        entity.setKeyHash(sha256(rawKey));
        entity.setEnabled(true);
        entity.setScopes(normalizeScopes(request.getScopes()));
        entity.setExpiresAt(request.getExpiresAt());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setTenantId(tenantId != null ? tenantId : SecurityUtils.getCurrentTenantId());
        ApiKey saved = apiKeyRepository.save(entity);
        CreateApiKeyResponse response = new CreateApiKeyResponse();
        copyToDto(saved, response);
        response.setRawKey(rawKey);
        return response;
    }

    @Transactional
    public ApiKeyDto update(Long id, UpdateApiKeyRequest request) {
        ApiKey entity = findById(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(request.getName().trim());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        if (request.getScopes() != null && !request.getScopes().isEmpty()) {
            entity.setScopes(normalizeScopes(request.getScopes()));
        }
        entity.setExpiresAt(request.getExpiresAt());
        entity.setUpdatedAt(Instant.now());
        return toDto(apiKeyRepository.save(entity));
    }

    @Transactional
    public CreateApiKeyResponse rotate(Long id) {
        ApiKey oldKey = findById(id);
        Instant graceExpires = Instant.now().plusSeconds(panelProperties.getApiKeyRotationGraceDays() * 86400L);
        oldKey.setDeprecated(true);
        oldKey.setExpiresAt(graceExpires);
        oldKey.setUpdatedAt(Instant.now());
        apiKeyRepository.save(oldKey);

        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName(oldKey.getName());
        request.setScopes(oldKey.getScopes());
        return createForTenant(request, oldKey.getTenantId());
    }

    @Transactional
    public void delete(Long id) {
        findById(id);
        apiKeyRepository.deleteById(id);
    }

    public Optional<ApiKey> validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256(rawKey.trim());
        Instant now = Instant.now();
        return apiKeyRepository.findByEnabledTrue().stream()
                .filter(k -> k.getKeyHash().equals(hash))
                .filter(k -> k.getExpiresAt() == null || k.getExpiresAt().isAfter(now))
                .findFirst();
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of("ai:chat");
        }
        List<String> normalized = scopes.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        for (String scope : normalized) {
            if (!VALID_SCOPES.contains(scope)) {
                throw new BusinessException("无效的 scope: " + scope);
            }
        }
        return normalized;
    }

    private ApiKey findById(Long id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("API 密钥不存在"));
        if (!SecurityUtils.isSuperAdmin() && !SecurityUtils.getCurrentTenantId().equals(key.getTenantId())) {
            throw new BusinessException("API 密钥不存在");
        }
        return key;
    }

    private ApiKeyDto toDto(ApiKey entity) {
        ApiKeyDto dto = new ApiKeyDto();
        copyToDto(entity, dto);
        return dto;
    }

    private void copyToDto(ApiKey entity, ApiKeyDto dto) {
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setKeyPrefix(maskPrefix(entity.getKeyPrefix()));
        dto.setEnabled(entity.isEnabled());
        dto.setScopes(entity.getScopes());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setDeprecated(entity.isDeprecated());
        dto.setTenantId(entity.getTenantId());
    }

    private String maskPrefix(String prefix) {
        if (prefix == null || prefix.length() <= 8) {
            return prefix + "****";
        }
        return prefix.substring(0, 8) + "****";
    }

    private String generateRawKey() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(KEY_PREFIX);
        for (int i = 0; i < KEY_RANDOM_LENGTH; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new BusinessException("密钥哈希失败");
        }
    }
}
