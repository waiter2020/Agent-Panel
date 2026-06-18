package com.agentpanel.application.repository;

import com.agentpanel.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByDeletedFalseOrderByUpdatedAtDesc();
    List<Application> findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(Long tenantId);
    List<Application> findByDeletedFalseAndRuntimeRefIsNotNull();
    Optional<Application> findByIdAndDeletedFalse(Long id);
    boolean existsByNameAndDeletedFalse(String name);
}
