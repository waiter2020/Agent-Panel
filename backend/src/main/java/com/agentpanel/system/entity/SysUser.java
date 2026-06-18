package com.agentpanel.system.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class SysUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false)
    private String password;

    private String nickname;
    private String email;
    private String phone;
    private String avatar;

    @Column(nullable = false, length = 16)
    private String status = "enabled";

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId = 1L;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<SysRole> roles = new HashSet<>();
}
