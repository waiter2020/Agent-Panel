package com.agentpanel.system.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_permission")
public class SysPermission extends BaseEntity {

    @Column(nullable = false, unique = true, length = 128)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 16)
    private String type = "api";

    @Column(name = "parent_id")
    private Long parentId;
}
