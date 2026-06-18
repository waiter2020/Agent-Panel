package com.agentpanel.application.repository;

import com.agentpanel.application.entity.AgentTopologyNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentTopologyNodeRepository extends JpaRepository<AgentTopologyNode, Long> {

    List<AgentTopologyNode> findByTopologyId(Long topologyId);

    List<AgentTopologyNode> findByApplicationId(Long applicationId);

    Optional<AgentTopologyNode> findByTopologyIdAndApplicationId(Long topologyId, Long applicationId);

    void deleteByTopologyIdAndApplicationId(Long topologyId, Long applicationId);

    long countByTopologyId(Long topologyId);
}
