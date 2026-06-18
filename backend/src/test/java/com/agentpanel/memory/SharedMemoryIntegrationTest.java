package com.agentpanel.memory;

import com.agentpanel.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.memory.dto.SharedMemoryDto;
import com.agentpanel.memory.dto.StoreMemoryRequest;
import com.agentpanel.memory.service.SharedMemoryService;

import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SharedMemoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("agentpanel")
            .withUsername("agentpanel")
            .withPassword("agentpanel");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @Autowired
    private SharedMemoryService sharedMemoryService;

    @BeforeEach
    void setUpAuth() {
        var principal = new AuthPrincipal(1L, "admin", 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("memory:write"),
                                new SimpleGrantedAuthority("memory:read"))));
    }

    @BeforeEach
    void setUpEmbedding() {
        float[] vector = new float[1536];
        vector[0] = 1.0f;
        vector[1] = 0.5f;
        when(embeddingModel.embed(anyString())).thenReturn(vector);
    }

    @Test
    void storeAndVectorSearch() {
        StoreMemoryRequest store = new StoreMemoryRequest();
        store.setKey("integration-test");
        store.setContent("pgvector integration test content");
        store.setScope("global");

        SharedMemoryDto stored = sharedMemoryService.store(store);
        assertNotNull(stored.getId());
        assertEquals("integration-test", stored.getKey());

        List<SharedMemoryDto> results = sharedMemoryService.search("integration test", null, null, 5);
        assertFalse(results.isEmpty());
        assertEquals(stored.getId(), results.getFirst().getId());
        assertNotNull(results.getFirst().getScore());
    }
}
