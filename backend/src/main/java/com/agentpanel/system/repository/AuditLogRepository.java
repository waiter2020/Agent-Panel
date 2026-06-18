package com.agentpanel.system.repository;

import com.agentpanel.system.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrderByAtDesc(Pageable pageable);

    @Query(value = """
            SELECT * FROM audit_log
            WHERE action = 'skill_reload_notify'
              AND resource_type = 'skill'
              AND detail->>'topologyId' = CAST(:topologyId AS TEXT)
              AND at > :since
            ORDER BY at ASC
            """, nativeQuery = true)
    List<AuditLog> findSkillReloadEventsSince(@Param("topologyId") Long topologyId, @Param("since") Instant since);
}
