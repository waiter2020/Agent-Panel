package com.agentpanel.application.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "agent_link")
public class AgentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topology_id", nullable = false)
    private Long topologyId;

    @Column(name = "from_node_id", nullable = false)
    private Long fromNodeId;

    @Column(name = "to_node_id", nullable = false)
    private Long toNodeId;

    @Column(nullable = false, length = 16)
    private String protocol = "http";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> config = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
