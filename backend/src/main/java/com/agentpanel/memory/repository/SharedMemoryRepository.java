package com.agentpanel.memory.repository;

import com.agentpanel.memory.entity.SharedMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SharedMemoryRepository extends JpaRepository<SharedMemory, Long> {

    List<SharedMemory> findAllByOrderByCreatedAtDesc();

    List<SharedMemory> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<SharedMemory> findByScopeOrderByCreatedAtDesc(String scope);

    List<SharedMemory> findByScopeAndTenantIdOrderByCreatedAtDesc(String scope, Long tenantId);

    List<SharedMemory> findByTopologyIdOrderByCreatedAtDesc(Long topologyId);

    List<SharedMemory> findByTopologyIdAndTenantIdOrderByCreatedAtDesc(Long topologyId, Long tenantId);

    List<SharedMemory> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    List<SharedMemory> findByApplicationIdAndTenantIdOrderByCreatedAtDesc(Long applicationId, Long tenantId);

    @Query("""
            SELECT m FROM SharedMemory m
            WHERE lower(m.content) LIKE lower(concat('%', :q, '%'))
               OR lower(m.key) LIKE lower(concat('%', :q, '%'))
            ORDER BY m.createdAt DESC
            """)
    List<SharedMemory> searchByKeyword(@Param("q") String q, Pageable pageable);

    @Query("""
            SELECT m FROM SharedMemory m
            WHERE m.tenantId = :tenantId
              AND (lower(m.content) LIKE lower(concat('%', :q, '%'))
                   OR lower(m.key) LIKE lower(concat('%', :q, '%')))
            ORDER BY m.createdAt DESC
            """)
    List<SharedMemory> searchByKeywordAndTenantId(
            @Param("q") String q,
            @Param("tenantId") Long tenantId,
            Pageable pageable);
}
