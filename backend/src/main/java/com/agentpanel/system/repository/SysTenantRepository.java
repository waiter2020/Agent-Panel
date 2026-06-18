package com.agentpanel.system.repository;

import com.agentpanel.system.entity.SysTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysTenantRepository extends JpaRepository<SysTenant, Long> {
    boolean existsByCode(String code);

    Optional<SysTenant> findByCode(String code);
}
