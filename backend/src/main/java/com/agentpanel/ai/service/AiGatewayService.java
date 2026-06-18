package com.agentpanel.ai.service;

import com.agentpanel.ai.dto.ChatRequest;
import com.agentpanel.ai.dto.ProviderDto;
import com.agentpanel.ai.entity.LlmModel;
import com.agentpanel.ai.entity.LlmProvider;
import com.agentpanel.ai.repository.LlmModelRepository;
import com.agentpanel.ai.repository.LlmProviderRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.CryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGatewayService {

    private final LlmProviderRepository providerRepository;
    private final LlmModelRepository modelRepository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public List<ProviderDto> listProviders() {
        return providerRepository.findByDeletedFalse().stream().map(this::toDto).toList();
    }

    @Transactional
    public ProviderDto createProvider(ProviderDto dto) {
        LlmProvider provider = new LlmProvider();
        provider.setCode(dto.getCode());
        provider.setName(dto.getName());
        provider.setType(dto.getType());
        provider.setBaseUrl(dto.getBaseUrl());
        provider.setEnabled(dto.isEnabled());
        provider.setConfig(dto.getConfig());
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
            provider.setApiKey(cryptoService.encrypt(dto.getApiKey()));
        }
        return toDto(providerRepository.save(provider));
    }

    @Transactional
    public ProviderDto updateProvider(Long id, ProviderDto dto) {
        LlmProvider provider = providerRepository.findById(id).filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException("Provider 不存在"));
        provider.setName(dto.getName());
        provider.setType(dto.getType());
        provider.setBaseUrl(dto.getBaseUrl());
        provider.setEnabled(dto.isEnabled());
        provider.setConfig(dto.getConfig());
        if (dto.getApiKey() != null && !dto.getApiKey().contains("****")) {
            provider.setApiKey(cryptoService.encrypt(dto.getApiKey()));
        }
        return toDto(providerRepository.save(provider));
    }

    @Transactional
    public void deleteProvider(Long id) {
        LlmProvider provider = providerRepository.findById(id).filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException("Provider 不存在"));
        provider.setDeleted(true);
        providerRepository.save(provider);
    }

    public List<LlmModel> listModels(Long providerId) {
        return modelRepository.findByProviderIdAndDeletedFalse(providerId);
    }

    @Transactional
    public LlmModel createModel(LlmModel model) {
        return modelRepository.save(model);
    }

    @Transactional
    public LlmModel updateModel(Long id, LlmModel model) {
        LlmModel existing = modelRepository.findById(id).filter(m -> !m.isDeleted())
                .orElseThrow(() -> new BusinessException("模型不存在"));
        existing.setModel(model.getModel());
        existing.setLabel(model.getLabel());
        existing.setCapabilities(model.getCapabilities());
        existing.setEnabled(model.isEnabled());
        return modelRepository.save(existing);
    }

    @Transactional
    public void deleteModel(Long id) {
        LlmModel model = modelRepository.findById(id).filter(m -> !m.isDeleted())
                .orElseThrow(() -> new BusinessException("模型不存在"));
        model.setDeleted(true);
        modelRepository.save(model);
    }

    public Flux<String> chat(ChatRequest request) {
        LlmProvider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new BusinessException("Provider 不存在"));
        log.info("AI 对话请求: provider={} model={} stream=true", provider.getCode(), request.getModel());
        long start = System.currentTimeMillis();
        return streamChat(provider, request.getModel(), toSpringMessages(request.getMessages()))
                .doOnComplete(() -> log.info(
                        "AI 对话完成: provider={} model={} latencyMs={}",
                        provider.getCode(),
                        request.getModel(),
                        System.currentTimeMillis() - start))
                .doOnError(e -> log.error(
                        "AI 对话失败: provider={} model={} latencyMs={}",
                        provider.getCode(),
                        request.getModel(),
                        System.currentTimeMillis() - start,
                        e));
    }

    public Map<String, Object> chatCompletions(Map<String, Object> body) {
        String model = stringValue(body.get("model"));
        LlmProvider provider = resolveProvider(model);
        log.info("AI 补全请求: provider={} model={} stream=false", provider.getCode(), model);
        long start = System.currentTimeMillis();
        try {
            ChatModel chatModel = buildChatModel(provider);
            var messages = parseOpenAiMessages(body.get("messages"));
            OpenAiChatOptions options = OpenAiChatOptions.builder().model(model).build();
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            String content = extractText(response);
            log.info("AI 补全完成: provider={} model={} latencyMs={}",
                    provider.getCode(), model, System.currentTimeMillis() - start);
            return buildCompletionResponse(model, content);
        } catch (Exception e) {
            log.error("AI 补全失败: provider={} model={} latencyMs={}",
                    provider.getCode(), model, System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    public Flux<String> chatCompletionsStream(Map<String, Object> body) {
        String model = stringValue(body.get("model"));
        LlmProvider provider = resolveProvider(model);
        log.info("AI 补全请求: provider={} model={} stream=true", provider.getCode(), model);
        long start = System.currentTimeMillis();
        String completionId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        long created = System.currentTimeMillis() / 1000;
        return streamChat(provider, model, parseOpenAiMessages(body.get("messages")))
                .doOnComplete(() -> log.info(
                        "AI 补全完成: provider={} model={} latencyMs={}",
                        provider.getCode(), model, System.currentTimeMillis() - start))
                .doOnError(e -> log.error(
                        "AI 补全失败: provider={} model={} latencyMs={}",
                        provider.getCode(), model, System.currentTimeMillis() - start, e))
                .map(chunk -> toJson(buildStreamChunk(completionId, model, created, chunk)))
                .concatWith(Flux.just("[DONE]"));
    }

    private Flux<String> streamChat(LlmProvider provider, String model,
                                    List<org.springframework.ai.chat.messages.Message> messages) {
        ChatModel chatModel = buildChatModel(provider);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(model).build();
        return chatModel.stream(new Prompt(messages, options))
                .mapNotNull(this::extractText);
    }

    private LlmProvider resolveProvider(String model) {
        if (model != null && !model.isBlank()) {
            var modelEntity = modelRepository.findFirstByModelAndDeletedFalseAndEnabledTrue(model);
            if (modelEntity.isPresent()) {
                return providerRepository.findById(modelEntity.get().getProviderId())
                        .filter(p -> !p.isDeleted() && p.isEnabled())
                        .orElseThrow(() -> new BusinessException("模型关联的 Provider 不可用"));
            }
        }
        return providerRepository.findFirstByDeletedFalseAndEnabledTrueOrderByIdAsc()
                .orElseThrow(() -> new BusinessException("未配置可用的 LLM Provider"));
    }

    @SuppressWarnings("unchecked")
    private List<org.springframework.ai.chat.messages.Message> parseOpenAiMessages(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new BusinessException("messages 格式无效");
        }
        var messages = new ArrayList<org.springframework.ai.chat.messages.Message>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String role = stringValue(map.get("role"));
            String content = stringValue(map.get("content"));
            switch (role) {
                case "system" -> messages.add(new SystemMessage(content));
                case "assistant" -> messages.add(new AssistantMessage(content));
                default -> messages.add(new UserMessage(content));
            }
        }
        if (messages.isEmpty()) {
            throw new BusinessException("messages 不能为空");
        }
        return messages;
    }

    private List<org.springframework.ai.chat.messages.Message> toSpringMessages(List<ChatRequest.Message> requestMessages) {
        var messages = new ArrayList<org.springframework.ai.chat.messages.Message>();
        for (ChatRequest.Message msg : requestMessages) {
            switch (msg.getRole()) {
                case "system" -> messages.add(new SystemMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                default -> messages.add(new UserMessage(msg.getContent()));
            }
        }
        return messages;
    }

    private Map<String, Object> buildCompletionResponse(String model, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content != null ? content : "");
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        response.put("object", "chat.completion");
        response.put("created", System.currentTimeMillis() / 1000);
        response.put("model", model);
        response.put("choices", List.of(choice));
        return response;
    }

    private Map<String, Object> buildStreamChunk(String id, String model, long created, String content) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("content", content);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", null);
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", created);
        chunk.put("model", model);
        chunk.put("choices", List.of(choice));
        return chunk;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractText(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private ChatModel buildChatModel(LlmProvider provider) {
        String apiKey = provider.getApiKey() != null ? cryptoService.decrypt(provider.getApiKey()) : "dummy";
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(provider.getBaseUrl())
                .apiKey(apiKey)
                .build();
        return OpenAiChatModel.builder().openAiApi(api).build();
    }

    private ProviderDto toDto(LlmProvider provider) {
        ProviderDto dto = new ProviderDto();
        dto.setId(provider.getId());
        dto.setCode(provider.getCode());
        dto.setName(provider.getName());
        dto.setType(provider.getType());
        dto.setBaseUrl(provider.getBaseUrl());
        dto.setEnabled(provider.isEnabled());
        dto.setConfig(provider.getConfig());
        if (provider.getApiKey() != null) {
            dto.setApiKey(cryptoService.mask(cryptoService.decrypt(provider.getApiKey())));
        }
        return dto;
    }
}
