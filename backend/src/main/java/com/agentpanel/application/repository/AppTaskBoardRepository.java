package com.agentpanel.application.repository;

import com.agentpanel.application.entity.AppTaskBoard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppTaskBoardRepository extends JpaRepository<AppTaskBoard, Long> {
    List<AppTaskBoard> findByDeletedFalseOrderByUpdatedAtDesc();

    List<AppTaskBoard> findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(Long tenantId);
    Optional<AppTaskBoard> findByIdAndDeletedFalse(Long id);
}
