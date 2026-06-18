package com.agentpanel.system.repository;

import com.agentpanel.system.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {
    List<SysMenu> findByDeletedFalseOrderByOrderNoAsc();
}
