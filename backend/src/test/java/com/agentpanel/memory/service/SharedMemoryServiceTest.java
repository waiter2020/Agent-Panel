package com.agentpanel.memory.service;

import com.agentpanel.application.repository.AgentTopologyRepository;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.config.MemoryProperties;
import com.agentpanel.memory.dto.SharedMemoryDto;
import com.agentpanel.memory.dto.StoreMemoryRequest;
import com.agentpanel.memory.entity.SharedMemory;
import com.agentpanel.memory.repository.SharedMemoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SharedMemoryServiceTest {

    @Mock private SharedMemoryRepository sharedMemoryRepository;
    @Mock private AgentTopologyRepository topologyRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ObjectProvider<EmbeddingModel> embeddingModelProvider;
    @Mock private EmbeddingModel embeddingModel;
    @Mock private MemoryProperties memoryProperties;

    @InjectMocks private SharedMemoryService sharedMemoryService;

    @Test
    void storeWithEmbeddingUpdatesVector() {
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        when(embeddingModel.embed("hello world")).thenReturn(new float[1536]);
        when(memoryProperties.getEmbeddingDimensions()).thenReturn(1536);

        SharedMemory saved = new SharedMemory();
        saved.setId(42L);
        saved.setKey("greeting");
        saved.setContent("hello world");
        saved.setScope("global");
        when(sharedMemoryRepository.save(any())).thenReturn(saved);

        StoreMemoryRequest request = new StoreMemoryRequest();
        request.setKey("greeting");
        request.setContent("hello world");
        request.setScope("global");

        SharedMemoryDto result = sharedMemoryService.store(request);

        assertEquals(42L, result.getId());
        assertEquals("greeting", result.getKey());
        verify(jdbcTemplate).update(contains("embedding"), anyString(), eq(42L));
    }

    @Test
    void searchFallsBackToKeywordWithoutEmbeddingModel() {
        when(embeddingModelProvider.getIfAvailable()).thenReturn(null);
        SharedMemory memory = new SharedMemory();
        memory.setId(1L);
        memory.setKey("doc");
        memory.setContent("vector database guide");
        memory.setScope("global");
        when(sharedMemoryRepository.searchByKeyword(eq("vector"), any(Pageable.class)))
                .thenReturn(List.of(memory));

        List<SharedMemoryDto> results = sharedMemoryService.search("vector", null, null, 10);

        assertEquals(1, results.size());
        assertEquals("doc", results.getFirst().getKey());
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any());
    }

    @Test
    void searchUsesVectorSimilarityWhenEmbeddingAvailable() {
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        when(embeddingModel.embed("query")).thenReturn(new float[1536]);
        when(memoryProperties.getEmbeddingDimensions()).thenReturn(1536);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), anyInt()))
                .thenReturn(List.of());

        sharedMemoryService.search("query", null, null, 5);

        verify(jdbcTemplate).query(contains("embedding <=>"), any(RowMapper.class), any(), any(), eq(5));
    }
}
