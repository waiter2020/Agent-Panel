package com.agentpanel.memory.repository;

import com.agentpanel.memory.entity.DelegationTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DelegationTraceRepository extends JpaRepository<DelegationTrace, Long> {

    List<DelegationTrace> findByTopologyIdOrderByStartedAtDesc(Long topologyId);
}
