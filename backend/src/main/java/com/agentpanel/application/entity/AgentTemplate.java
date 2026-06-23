package com.agentpanel.application.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "agent_template")
public class AgentTemplate extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    private String description;
    private String icon;

    @Column(nullable = false, length = 512)
    private String image;

    @Column(name = "default_tag", nullable = false, length = 128)
    private String defaultTag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "port_schema", nullable = false)
    private List<Map<String, Object>> portSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "env_schema", nullable = false)
    private List<Map<String, Object>> envSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "volume_schema", nullable = false)
    private List<Map<String, Object>> volumeSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_resources", nullable = false)
    private Map<String, Object> defaultResources;

    @Column(nullable = false)
    private boolean builtin = true;

    @Column(name = "doc_url")
    private String docUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "management_schema", nullable = false)
    private Map<String, Object> managementSchema = new HashMap<>();
}
