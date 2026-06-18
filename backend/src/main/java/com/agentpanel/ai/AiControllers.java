package com.agentpanel.ai;

import com.agentpanel.ai.dto.ChatRequest;
import com.agentpanel.ai.dto.ProviderDto;
import com.agentpanel.ai.entity.LlmModel;
import com.agentpanel.ai.service.AiGatewayService;
import com.agentpanel.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AiControllers {

    private final AiGatewayService aiGatewayService;

    @GetMapping("/api/ai/providers")
    @PreAuthorize("hasAuthority('ai:read')")
    public ApiResponse<List<ProviderDto>> listProviders() {
        return ApiResponse.ok(aiGatewayService.listProviders());
    }

    @PostMapping("/api/ai/providers")
    @PreAuthorize("hasAuthority('ai:write')")
    public ApiResponse<ProviderDto> createProvider(@RequestBody ProviderDto dto) {
        return ApiResponse.ok(aiGatewayService.createProvider(dto));
    }

    @PutMapping("/api/ai/providers/{id}")
    @PreAuthorize("hasAuthority('ai:write')")
    public ApiResponse<ProviderDto> updateProvider(@PathVariable Long id, @RequestBody ProviderDto dto) {
        return ApiResponse.ok(aiGatewayService.updateProvider(id, dto));
    }

    @DeleteMapping("/api/ai/providers/{id}")
    @PreAuthorize("hasAuthority('ai:write')")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id) {
        aiGatewayService.deleteProvider(id);
        return ApiResponse.ok();
    }

    @GetMapping("/api/ai/models")
    @PreAuthorize("hasAuthority('ai:read')")
    public ApiResponse<List<LlmModel>> listModels(@RequestParam Long providerId) {
        return ApiResponse.ok(aiGatewayService.listModels(providerId));
    }

    @PostMapping("/api/ai/models")
    @PreAuthorize("hasAuthority('ai:write')")
    public ApiResponse<LlmModel> createModel(@RequestBody LlmModel model) {
        return ApiResponse.ok(aiGatewayService.createModel(model));
    }

    @PutMapping("/api/ai/models/{id}")
    @PreAuthorize("hasAuthority('ai:write')")
    public ApiResponse<LlmModel> updateModel(@PathVariable Long id, @RequestBody LlmModel model) {
        return ApiResponse.ok(aiGatewayService.updateModel(id, model));
    }

    @DeleteMapping("/api/ai/models/{id}")
    @PreAuthorize("hasAuthority('ai:write')")
    public ApiResponse<Void> deleteModel(@PathVariable Long id) {
        aiGatewayService.deleteModel(id);
        return ApiResponse.ok();
    }

    @PostMapping(value = "/api/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ai:chat')")
    public Flux<String> chat(@RequestBody ChatRequest request) {
        return aiGatewayService.chat(request);
    }

    @PostMapping("/v1/chat/completions")
    @PreAuthorize("hasAuthority('ai:chat')")
    public Object chatCompletions(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        if (Boolean.TRUE.equals(body.get("stream"))) {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            return aiGatewayService.chatCompletionsStream(body);
        }
        return aiGatewayService.chatCompletions(body);
    }
}
