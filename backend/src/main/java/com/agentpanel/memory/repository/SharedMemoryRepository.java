package com.agentpanel.memory.repository;

import com.agentpanel.memory.entity.SharedMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SharedMemoryRepository extends JpaRepository<SharedMemory, Long> {

    List<SharedMemory> findAllByOrderByCreatedAtDesc();

    List<SharedMemory> findByScopeOrderByCreatedAtDesc(String scope);

    List<SharedMemory> findByTopologyIdOrderByCreatedAtDesc(Long topologyId);

    List<SharedMemory> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    @Query("""
            SELECT m FROM SharedMemory m
            WHERE lower(m.content) LIKE lower(concat('%', :q, '%'))
               OR lower(m.key) LIKE lower(concat('%', :q, '%'))
            ORDER BY m.createdAt DESC
            """)
    List<SharedMemory> searchByKeyword(@Param("q") String q, Pageable pageable);
}
