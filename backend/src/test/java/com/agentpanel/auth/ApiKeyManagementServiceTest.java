package com.agentpanel.auth;

import com.agentpanel.auth.dto.CreateApiKeyRequest;
import com.agentpanel.auth.entity.ApiKey;
import com.agentpanel.auth.repository.ApiKeyRepository;
import com.agentpanel.config.AgentPanelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyManagementServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private AgentPanelProperties panelProperties;

    @InjectMocks
    private ApiKeyManagementService apiKeyManagementService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(panelProperties.getApiKeyRotationGraceDays()).thenReturn(7);
    }

    @Test
    void createReturnsRawKeyOnce() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(1L);
            return key;
        });

        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("测试密钥");
        request.setScopes(List.of("ai:chat"));

        var response = apiKeyManagementService.create(request);

        assertNotNull(response.getRawKey());
        assertTrue(response.getRawKey().startsWith("apk_"));
        assertEquals("测试密钥", response.getName());
        assertTrue(response.getKeyPrefix().endsWith("****"));
    }

    @Test
    void validateAcceptsActiveKey() {
        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(captor.capture())).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(1L);
            return key;
        });

        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("推理密钥");
        request.setScopes(List.of("ai:chat"));
        var created = apiKeyManagementService.create(request);
        ApiKey stored = captor.getValue();

        when(apiKeyRepository.findByEnabledTrue()).thenReturn(List.of(stored));

        Optional<ApiKey> validated = apiKeyManagementService.validate(created.getRawKey());
        assertTrue(validated.isPresent());
        assertEquals("推理密钥", validated.get().getName());
    }

    @Test
    void validateRejectsExpiredKey() {
        ApiKey expired = new ApiKey();
        expired.setName("过期密钥");
        expired.setKeyPrefix("apk_expired1234");
        expired.setKeyHash(sha256("apk_expired_key_value_1234567890"));
        expired.setEnabled(true);
        expired.setScopes(List.of("ai:chat"));
        expired.setExpiresAt(java.time.Instant.now().minusSeconds(60));
        when(apiKeyRepository.findByEnabledTrue()).thenReturn(List.of(expired));

        assertTrue(apiKeyManagementService.validate("apk_expired_key_value_1234567890").isEmpty());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
