package com.agentpanel.application.repository;

import com.agentpanel.application.entity.AgentLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentLinkRepository extends JpaRepository<AgentLink, Long> {

    List<AgentLink> findByTopologyId(Long topologyId);

    Optional<AgentLink> findByIdAndTopologyId(Long id, Long topologyId);

    void deleteByTopologyIdAndFromNodeId(Long topologyId, Long fromNodeId);

    void deleteByTopologyId(Long topologyId);
}
