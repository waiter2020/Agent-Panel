package com.agentpanel.registry;

import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.common.ApiResponse;
import com.agentpanel.registry.dto.RegistrySourceDto;
import com.agentpanel.registry.dto.RegistryTagsDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistryControllerTest {

    @Mock private RegistryService registryService;
    @Mock private AgentTemplateRepository templateRepository;
    @InjectMocks private RegistryController registryController;

    @Test
    void listSourcesReturnsGhcrAndDockerHub() {
        ApiResponse<List<RegistrySourceDto>> response = registryController.listSources();
        assertEquals(0, response.getCode());
        assertEquals(3, response.getData().size());
        assertEquals("ghcr", response.getData().getFirst().getId());
        assertEquals("dockerhub", response.getData().get(1).getId());
        assertEquals("custom", response.getData().get(2).getId());
    }

    @Test
    void listTagsDelegatesToService() {
        RegistryTagsDto dto = RegistryTagsDto.builder()
                .tags(List.of("v1.0.0", "v0.9.0"))
                .defaultTag("v1.0.0")
                .source("registry")
                .fallback(false)
                .build();
        when(registryService.listTags(eq("ghcr.io/openclaw/openclaw"), eq(null))).thenReturn(dto);

        ApiResponse<RegistryTagsDto> response = registryController.listTags("ghcr.io/openclaw/openclaw", null);

        assertEquals(0, response.getCode());
        assertEquals(2, response.getData().getTags().size());
        assertEquals("v1.0.0", response.getData().getDefaultTag());
    }
}
