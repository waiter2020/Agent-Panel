package com.agentpanel.application.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "app_task_board")
public class AppTaskBoard extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType = "platform";

    @Column(name = "scope_ref", length = 64)
    private String scopeRef;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId = 1L;
}
