package com.agentpanel.memory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "delegation_trace")
public class DelegationTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topology_id", nullable = false)
    private Long topologyId;

    @Column(name = "parent_app_id", nullable = false)
    private Long parentAppId;

    @Column(name = "child_app_id", nullable = false)
    private Long childAppId;

    @Column(name = "task_summary", nullable = false, columnDefinition = "TEXT")
    private String taskSummary;

    @Column(nullable = false, length = 32)
    private String status = "running";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> result = Map.of();
}
