package com.agentpanel.application.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agent_topology")
public class AgentTopology extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "network_name", nullable = false, length = 128)
    private String networkName = "agentpanel-net";

    @Column(nullable = false, length = 32)
    private String status = "draft";

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "inference_api_key_id")
    private Long inferenceApiKeyId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId = 1L;
}
