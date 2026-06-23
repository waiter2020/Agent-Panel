package com.agentpanel.memory.service;

import com.agentpanel.application.entity.AgentTopology;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTopologyRepository;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.TenantAccessHelper;
import com.agentpanel.config.MemoryProperties;
import com.agentpanel.memory.dto.SharedMemoryDto;
import com.agentpanel.memory.dto.StoreMemoryRequest;
import com.agentpanel.memory.entity.SharedMemory;
import com.agentpanel.memory.repository.SharedMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-agent shared memory with optional pgvector semantic search.
 * <p>
 * When a Spring AI {@link EmbeddingModel} bean is available (OpenAI or Ollama via
 * {@code spring.ai.openai.*} / {@code spring.ai.ollama.*}), {@link #store} embeds content
 * and persists the vector; {@link #search} uses cosine distance ({@code <=>}) ranking.
 * If no model is configured or embedding fails, storage proceeds without a vector and
 * search falls back to PostgreSQL keyword matching ({@code searchByKeyword}).
 */
@Service
@RequiredArgsConstructor
public class SharedMemoryService {

    private static final Set<String> SCOPES = Set.of("global", "topology", "app");

    private final SharedMemoryRepository sharedMemoryRepository;
    private final AgentTopologyRepository topologyRepository;
    private final ApplicationRepository applicationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final MemoryProperties memoryProperties;

    @Transactional
    public SharedMemoryDto store(StoreMemoryRequest request) {
        if (request.getKey() == null || request.getKey().isBlank()) {
            throw new BusinessException("记忆键不能为空");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BusinessException("记忆内容不能为空");
        }
        String scope = normalizeScope(request.getScope());
        Long tenantId = validateScopeAndResolveTenantId(scope, request.getTopologyId(), request.getApplicationId());

        SharedMemory memory = new SharedMemory();
        memory.setKey(request.getKey().trim());
        memory.setContent(request.getContent());
        memory.setScope(scope);
        memory.setTopologyId(request.getTopologyId());
        memory.setApplicationId(request.getApplicationId());
        memory.setTenantId(tenantId);
        memory.setMetadata(request.getMetadata() == null ? Map.of() : request.getMetadata());
        memory.setCreatedBy(SecurityUtils.getCurrentUserId());
        memory = sharedMemoryRepository.save(memory);

        float[] embedding = embedIfAvailable(request.getContent());
        if (embedding != null) {
            jdbcTemplate.update(
                    "UPDATE shared_memory SET embedding = ?::vector WHERE id = ?",
                    toVectorLiteral(embedding),
                    memory.getId());
        }
        return toDto(memory, null);
    }

    public List<SharedMemoryDto> search(String query, Long topologyId, Long applicationId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 50);
        Long tenantFilter = resolveReadTenantId(topologyId, applicationId);
        if (query == null || query.isBlank()) {
            return list(topologyId, applicationId, scopeFromFilters(topologyId, applicationId)).stream()
                    .limit(capped)
                    .toList();
        }

        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model != null) {
            float[] queryEmbedding = model.embed(query);
            String vectorLiteral = toVectorLiteral(queryEmbedding);
            StringBuilder sql = new StringBuilder("""
                    SELECT id, topology_id, application_id, scope, key, content, metadata, created_at, created_by,
                           1 - (embedding <=> ?::vector) AS score
                    FROM shared_memory
                    WHERE embedding IS NOT NULL
                    """);
            List<Object> params = new ArrayList<>();
            params.add(vectorLiteral);
            if (tenantFilter != null) {
                sql.append(" AND tenant_id = ?");
                params.add(tenantFilter);
            }
            if (topologyId != null) {
                sql.append(" AND topology_id = ?");
                params.add(topologyId);
            }
            if (applicationId != null) {
                sql.append(" AND application_id = ?");
                params.add(applicationId);
            }
            sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
            params.add(vectorLiteral);
            params.add(capped);
            return jdbcTemplate.query(
                    sql.toString(),
                    (rs, rowNum) -> mapRow(rs),
                    params.toArray());
        }

        List<SharedMemory> matches = tenantFilter == null
                ? sharedMemoryRepository.searchByKeyword(query.trim(), PageRequest.of(0, capped))
                : sharedMemoryRepository.searchByKeywordAndTenantId(query.trim(), tenantFilter, PageRequest.of(0, capped));
        return matches.stream()
                .filter(m -> matchesFilters(m, topologyId, applicationId))
                .map(m -> toDto(m, null))
                .toList();
    }

    public List<SharedMemoryDto> list(Long topologyId, Long applicationId, String scope) {
        Long tenantFilter = resolveReadTenantId(topologyId, applicationId);
        List<SharedMemory> memories;
        if (topologyId != null && applicationId != null) {
            memories = findByTopology(topologyId, tenantFilter).stream()
                    .filter(m -> applicationId.equals(m.getApplicationId()))
                    .toList();
        } else if (topologyId != null) {
            memories = findByTopology(topologyId, tenantFilter);
        } else if (applicationId != null) {
            memories = findByApplication(applicationId, tenantFilter);
        } else if (scope != null && !scope.isBlank()) {
            memories = findByScope(normalizeScope(scope), tenantFilter);
        } else {
            memories = tenantFilter == null
                    ? sharedMemoryRepository.findAllByOrderByCreatedAtDesc()
                    : sharedMemoryRepository.findByTenantIdOrderByCreatedAtDesc(tenantFilter);
        }
        return memories.stream().map(m -> toDto(m, null)).toList();
    }

    @Transactional
    public void delete(Long id) {
        SharedMemory memory = sharedMemoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("记忆不存在"));
        TenantAccessHelper.requireOwnedTenant(memory.getTenantId(), "记忆不存在");
        sharedMemoryRepository.delete(memory);
    }

    private float[] embedIfAvailable(String content) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            return null;
        }
        try {
            return model.embed(content);
        } catch (Exception e) {
            return null;
        }
    }

    private Long validateScopeAndResolveTenantId(String scope, Long topologyId, Long applicationId) {
        Long tenantId;
        if ("topology".equals(scope)) {
            if (topologyId == null) {
                throw new BusinessException("拓扑范围记忆需要 topologyId");
            }
            AgentTopology topology = topologyRepository.findByIdAndDeletedFalse(topologyId)
                    .orElseThrow(() -> new BusinessException("拓扑不存在"));
            TenantAccessHelper.requireOwnedTenant(topology.getTenantId(), "拓扑不存在");
            tenantId = topology.getTenantId();
        } else if ("app".equals(scope)) {
            if (applicationId == null) {
                throw new BusinessException("应用范围记忆需要 applicationId");
            }
            Application app = applicationRepository.findByIdAndDeletedFalse(applicationId)
                    .orElseThrow(() -> new BusinessException("应用不存在"));
            TenantAccessHelper.requireOwnedTenant(app.getTenantId(), "应用不存在");
            tenantId = app.getTenantId();
        } else {
            tenantId = SecurityUtils.getCurrentTenantId();
        }
        if (topologyId != null) {
            AgentTopology topology = topologyRepository.findByIdAndDeletedFalse(topologyId)
                    .orElseThrow(() -> new BusinessException("拓扑不存在"));
            TenantAccessHelper.requireOwnedTenant(topology.getTenantId(), "拓扑不存在");
            if (!tenantId.equals(topology.getTenantId())) {
                throw new BusinessException("拓扑不存在");
            }
        }
        if (applicationId != null) {
            Application app = applicationRepository.findByIdAndDeletedFalse(applicationId)
                    .orElseThrow(() -> new BusinessException("应用不存在"));
            TenantAccessHelper.requireOwnedTenant(app.getTenantId(), "应用不存在");
            if (!tenantId.equals(app.getTenantId())) {
                throw new BusinessException("应用不存在");
            }
        }
        return tenantId;
    }

    private Long resolveReadTenantId(Long topologyId, Long applicationId) {
        Long tenantId = SecurityUtils.isSuperAdmin() ? null : SecurityUtils.getCurrentTenantId();
        if (topologyId != null) {
            AgentTopology topology = topologyRepository.findByIdAndDeletedFalse(topologyId)
                    .orElseThrow(() -> new BusinessException("拓扑不存在"));
            TenantAccessHelper.requireOwnedTenant(topology.getTenantId(), "拓扑不存在");
            tenantId = topology.getTenantId();
        }
        if (applicationId != null) {
            Application app = applicationRepository.findByIdAndDeletedFalse(applicationId)
                    .orElseThrow(() -> new BusinessException("应用不存在"));
            TenantAccessHelper.requireOwnedTenant(app.getTenantId(), "应用不存在");
            if (tenantId != null && !tenantId.equals(app.getTenantId())) {
                throw new BusinessException("应用不存在");
            }
            tenantId = app.getTenantId();
        }
        return tenantId;
    }

    private List<SharedMemory> findByTopology(Long topologyId, Long tenantId) {
        return tenantId == null
                ? sharedMemoryRepository.findByTopologyIdOrderByCreatedAtDesc(topologyId)
                : sharedMemoryRepository.findByTopologyIdAndTenantIdOrderByCreatedAtDesc(topologyId, tenantId);
    }

    private List<SharedMemory> findByApplication(Long applicationId, Long tenantId) {
        return tenantId == null
                ? sharedMemoryRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)
                : sharedMemoryRepository.findByApplicationIdAndTenantIdOrderByCreatedAtDesc(applicationId, tenantId);
    }

    private List<SharedMemory> findByScope(String scope, Long tenantId) {
        return tenantId == null
                ? sharedMemoryRepository.findByScopeOrderByCreatedAtDesc(scope)
                : sharedMemoryRepository.findByScopeAndTenantIdOrderByCreatedAtDesc(scope, tenantId);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "global";
        }
        String normalized = scope.trim().toLowerCase();
        if (!SCOPES.contains(normalized)) {
            throw new BusinessException("scope 必须为 global、topology 或 app");
        }
        return normalized;
    }

    private String scopeFromFilters(Long topologyId, Long applicationId) {
        if (applicationId != null) {
            return "app";
        }
        if (topologyId != null) {
            return "topology";
        }
        return null;
    }

    private boolean matchesFilters(SharedMemory memory, Long topologyId, Long applicationId) {
        if (topologyId != null && !topologyId.equals(memory.getTopologyId())) {
            return false;
        }
        return applicationId == null || applicationId.equals(memory.getApplicationId());
    }

    private SharedMemoryDto mapRow(ResultSet rs) throws SQLException {
        SharedMemoryDto dto = new SharedMemoryDto();
        dto.setId(rs.getLong("id"));
        dto.setTopologyId(rs.getObject("topology_id") != null ? rs.getLong("topology_id") : null);
        dto.setApplicationId(rs.getObject("application_id") != null ? rs.getLong("application_id") : null);
        dto.setScope(rs.getString("scope"));
        dto.setKey(rs.getString("key"));
        dto.setContent(rs.getString("content"));
        dto.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        dto.setCreatedBy(rs.getObject("created_by") != null ? rs.getLong("created_by") : null);
        dto.setScore(rs.getDouble("score"));
        return dto;
    }

    private SharedMemoryDto toDto(SharedMemory memory, Double score) {
        SharedMemoryDto dto = new SharedMemoryDto();
        dto.setId(memory.getId());
        dto.setTopologyId(memory.getTopologyId());
        dto.setApplicationId(memory.getApplicationId());
        dto.setScope(memory.getScope());
        dto.setKey(memory.getKey());
        dto.setContent(memory.getContent());
        dto.setMetadata(memory.getMetadata());
        dto.setCreatedAt(memory.getCreatedAt());
        dto.setCreatedBy(memory.getCreatedBy());
        dto.setScore(score);
        return dto;
    }

    private String toVectorLiteral(float[] values) {
        int dim = memoryProperties.getEmbeddingDimensions();
        if (values.length != dim) {
            throw new BusinessException("嵌入向量维度不匹配，期望 " + dim + " 实际 " + values.length);
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
