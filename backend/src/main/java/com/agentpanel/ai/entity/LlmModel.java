package com.agentpanel.ai.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "llm_model")
public class LlmModel extends BaseEntity {

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(nullable = false, length = 128)
    private String model;

    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> capabilities;

    @Column(nullable = false)
    private boolean enabled = true;
}
