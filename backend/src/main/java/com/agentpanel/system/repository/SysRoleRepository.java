package com.agentpanel.system.repository;

import com.agentpanel.system.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysRoleRepository extends JpaRepository<SysRole, Long> {
    List<SysRole> findByDeletedFalse();
    Optional<SysRole> findByIdAndDeletedFalse(Long id);
    Optional<SysRole> findByCodeAndDeletedFalse(String code);
}
