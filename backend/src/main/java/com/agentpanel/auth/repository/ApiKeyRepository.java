package com.agentpanel.auth.repository;

import com.agentpanel.auth.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findByEnabledTrue();

    List<ApiKey> findAllByOrderByCreatedAtDesc();
}
