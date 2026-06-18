package com.agentpanel.application.repository;

import com.agentpanel.application.entity.AppDeployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppDeploymentRepository extends JpaRepository<AppDeployment, Long> {
    List<AppDeployment> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
    Optional<AppDeployment> findFirstByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
