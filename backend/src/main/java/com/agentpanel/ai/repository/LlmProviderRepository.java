package com.agentpanel.ai.repository;

import com.agentpanel.ai.entity.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LlmProviderRepository extends JpaRepository<LlmProvider, Long> {
    List<LlmProvider> findByDeletedFalse();
    Optional<LlmProvider> findByCodeAndDeletedFalse(String code);
    Optional<LlmProvider> findFirstByDeletedFalseAndEnabledTrueOrderByIdAsc();
}
