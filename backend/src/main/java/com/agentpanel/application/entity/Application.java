package com.agentpanel.application.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "application")
public class Application extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    private String image;
    private String tag;

    @Column(nullable = false, length = 32)
    private String status = "created";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> ports;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> resources;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> volumes;

    @Column(nullable = false)
    private int replicas = 1;

    @Column(name = "runtime_provider", length = 16)
    private String runtimeProvider;

    private String remark;

    @Column(name = "runtime_ref")
    private String runtimeRef;

    @Column(name = "runtime_namespace")
    private String runtimeNamespace;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId = 1L;
}
