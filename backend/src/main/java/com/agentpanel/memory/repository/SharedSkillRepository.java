package com.agentpanel.memory.repository;

import com.agentpanel.memory.entity.SharedSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SharedSkillRepository extends JpaRepository<SharedSkill, Long> {

    List<SharedSkill> findByTopologyIdOrderByNameAsc(Long topologyId);

    List<SharedSkill> findByApplicationIdOrderByNameAsc(Long applicationId);

    List<SharedSkill> findByTopologyIdAndApplicationIdOrderByNameAsc(Long topologyId, Long applicationId);

    Optional<SharedSkill> findByIdAndTopologyId(Long id, Long topologyId);

    boolean existsByTopologyIdAndName(Long topologyId, String name);
}
