package com.agentpanel.application.repository;

import com.agentpanel.application.entity.McpEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McpEndpointRepository extends JpaRepository<McpEndpoint, Long> {

    List<McpEndpoint> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    Optional<McpEndpoint> findByIdAndApplicationId(Long id, Long applicationId);
}
