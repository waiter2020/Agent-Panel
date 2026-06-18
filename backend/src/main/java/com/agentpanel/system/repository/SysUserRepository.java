package com.agentpanel.system.repository;

import com.agentpanel.system.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {
    Optional<SysUser> findByUsernameAndDeletedFalse(String username);
    boolean existsByUsernameAndDeletedFalse(String username);
    Page<SysUser> findByDeletedFalseAndTenantId(Long tenantId, Pageable pageable);
}
