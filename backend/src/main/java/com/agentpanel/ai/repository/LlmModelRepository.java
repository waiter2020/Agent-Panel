package com.agentpanel.ai.repository;

import com.agentpanel.ai.entity.LlmModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LlmModelRepository extends JpaRepository<LlmModel, Long> {
    List<LlmModel> findByProviderIdAndDeletedFalse(Long providerId);
    List<LlmModel> findByDeletedFalseAndEnabledTrue();
    Optional<LlmModel> findFirstByModelAndDeletedFalseAndEnabledTrue(String model);
}
