package com.agentpanel.application.repository;

import com.agentpanel.application.entity.AppEnv;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppEnvRepository extends JpaRepository<AppEnv, Long> {
    List<AppEnv> findByApplicationId(Long applicationId);
    void deleteByApplicationId(Long applicationId);
}
