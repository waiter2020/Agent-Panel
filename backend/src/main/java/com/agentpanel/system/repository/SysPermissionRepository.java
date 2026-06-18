package com.agentpanel.system.repository;

import com.agentpanel.system.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {
    List<SysPermission> findByDeletedFalse();
}
