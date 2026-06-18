package com.agentpanel.registry;

import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.common.ApiResponse;
import com.agentpanel.registry.dto.RegistrySourceDto;
import com.agentpanel.registry.dto.RegistryTagsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RegistryController {

    private static final List<RegistrySourceDto> SOURCES = List.of(
            new RegistrySourceDto("ghcr", "GHCR", "ghcr.io", "GitHub Container Registry"),
            new RegistrySourceDto("dockerhub", "Docker Hub", "docker.io", "Docker 官方镜像仓库"),
            new RegistrySourceDto("custom", "自定义", "", "手动输入完整镜像地址")
    );

    private final RegistryService registryService;
    private final AgentTemplateRepository templateRepository;

    @GetMapping("/api/registry/sources")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<List<RegistrySourceDto>> listSources() {
        return ApiResponse.ok(SOURCES);
    }

    @GetMapping("/api/registry/tags")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<RegistryTagsDto> listTags(
            @RequestParam String image,
            @RequestParam(required = false) Long templateId) {
        String defaultTag = resolveDefaultTag(templateId);
        return ApiResponse.ok(registryService.listTags(image, defaultTag));
    }

    private String resolveDefaultTag(Long templateId) {
        if (templateId == null) {
            return null;
        }
        return templateRepository.findById(templateId)
                .map(AgentTemplate::getDefaultTag)
                .orElse(null);
    }
}
