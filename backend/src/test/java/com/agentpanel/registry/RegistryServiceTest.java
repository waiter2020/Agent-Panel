package com.agentpanel.registry;

import com.agentpanel.config.RegistryProperties;
import com.agentpanel.registry.dto.RegistryTagsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RegistryServiceTest {

    private RegistryService registryService;

    @BeforeEach
    void setUp() {
        RegistryProperties properties = new RegistryProperties();
        properties.setCacheTtl(Duration.ofMinutes(10));
        properties.setRequestTimeout(Duration.ofSeconds(5));
        registryService = new RegistryService(properties, WebClient.builder(), new ObjectMapper());
    }

    @Test
    void sortTagsOrdersVersionsDescendingWithLatestLast() {
        List<String> sorted = RegistryService.sortTags(List.of("latest", "v0.1.0", "v1.0.0", "v0.9.0"));
        assertEquals(List.of("v1.0.0", "v0.9.0", "v0.1.0", "latest"), sorted);
    }

    @Test
    void listTagsReturnsFallbackForDisallowedHost() {
        RegistryTagsDto result = registryService.listTags("registry.internal.local/app", "v1.0.0");
        assertTrue(result.isFallback());
        assertEquals("fallback", result.getSource());
        assertEquals("v1.0.0", result.getDefaultTag());
        assertTrue(result.getTags().isEmpty());
        assertNotNull(result.getMessage());
    }

    @Test
    void listTagsReturnsFallbackForBlockedLocalhost() {
        RegistryTagsDto result = registryService.listTags("localhost:5000/my/app", "v1.0.0");
        assertTrue(result.isFallback());
        assertNotNull(result.getMessage());
    }
}
