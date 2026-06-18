package com.agentpanel.system.repository;

import com.agentpanel.system.entity.SysSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysSettingRepository extends JpaRepository<SysSetting, Long> {
    Optional<SysSetting> findByKey(String key);
    List<SysSetting> findAllByOrderByKeyAsc();
}
