package com.agentpanel.application.repository;

import com.agentpanel.application.entity.AgentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentTemplateRepository extends JpaRepository<AgentTemplate, Long> {
    List<AgentTemplate> findByDeletedFalse();
    Optional<AgentTemplate> findByCodeAndDeletedFalse(String code);
}
