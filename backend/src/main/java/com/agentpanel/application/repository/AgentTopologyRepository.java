package com.agentpanel.application.repository;

import com.agentpanel.application.entity.AgentTopology;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentTopologyRepository extends JpaRepository<AgentTopology, Long> {

    List<AgentTopology> findByDeletedFalseOrderByUpdatedAtDesc();

    List<AgentTopology> findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(Long tenantId);

    Optional<AgentTopology> findByIdAndDeletedFalse(Long id);

    boolean existsByNameAndTenantIdAndDeletedFalse(String name, Long tenantId);
}
