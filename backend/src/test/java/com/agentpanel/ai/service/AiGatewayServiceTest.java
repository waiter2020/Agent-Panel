package com.agentpanel.ai.service;

import com.agentpanel.ai.dto.ChatRequest;
import com.agentpanel.ai.entity.LlmProvider;
import com.agentpanel.ai.repository.LlmModelRepository;
import com.agentpanel.ai.repository.LlmProviderRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.CryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {

    @Mock
    private LlmProviderRepository providerRepository;

    @Mock
    private LlmModelRepository modelRepository;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AiGatewayService aiGatewayService;

    @Test
    void chatRejectsDisabledProvider() {
        LlmProvider provider = provider(false, false);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        assertThrows(BusinessException.class, () -> aiGatewayService.chat(request(1L)));

        verifyNoInteractions(modelRepository, cryptoService, objectMapper);
    }

    @Test
    void chatRejectsDeletedProvider() {
        LlmProvider provider = provider(true, true);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        assertThrows(BusinessException.class, () -> aiGatewayService.chat(request(1L)));

        verifyNoInteractions(modelRepository, cryptoService, objectMapper);
    }

    private ChatRequest request(Long providerId) {
        ChatRequest request = new ChatRequest();
        request.setProviderId(providerId);
        request.setModel("gpt-test");
        return request;
    }

    private LlmProvider provider(boolean deleted, boolean enabled) {
        LlmProvider provider = new LlmProvider();
        provider.setId(1L);
        provider.setCode("test");
        provider.setName("Test");
        provider.setType("openai");
        provider.setBaseUrl("https://example.test/v1");
        provider.setDeleted(deleted);
        provider.setEnabled(enabled);
        return provider;
    }
}
