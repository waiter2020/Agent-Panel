package com.agentpanel.system.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_menu")
public class SysMenu extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    private String path;
    private String icon;
    private String component;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "order_no", nullable = false)
    private int orderNo = 0;

    @Column(name = "permission_id")
    private Long permissionId;

    @Column(nullable = false)
    private boolean hidden = false;
}
