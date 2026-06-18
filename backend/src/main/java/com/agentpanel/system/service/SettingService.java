package com.agentpanel.system.service;

import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.config.StorageProperties;
import com.agentpanel.system.entity.SysSetting;
import com.agentpanel.system.repository.SysSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingService {

    private final SysSettingRepository settingRepository;
    private final StorageProperties storageProperties;
    private final AgentRuntimeProperties agentRuntimeProperties;

    public Map<String, String> getAll() {
        Map<String, String> settings = new LinkedHashMap<>();
        settingRepository.findAllByOrderByKeyAsc()
                .forEach(s -> settings.put(s.getKey(), s.getValue()));
        settings.put("runtime.provider", agentRuntimeProperties.getProvider());
        settings.putIfAbsent("storage.endpoint", storageProperties.endpoint());
        settings.putIfAbsent("storage.region", storageProperties.region());
        settings.putIfAbsent("storage.bucket", storageProperties.bucket());
        settings.putIfAbsent("storage.accessKey", storageProperties.accessKey());
        settings.putIfAbsent("storage.secretKey", maskSecret(storageProperties.secretKey()));
        return settings;
    }

    @Transactional
    public Map<String, String> update(Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            if (entry.getKey().startsWith("storage.") || entry.getKey().startsWith("runtime.")) {
                continue;
            }
            SysSetting setting = settingRepository.findByKey(entry.getKey())
                    .orElseGet(() -> {
                        SysSetting created = new SysSetting();
                        created.setKey(entry.getKey());
                        return created;
                    });
            setting.setValue(entry.getValue());
            setting.setUpdatedAt(Instant.now());
            settingRepository.save(setting);
        }
        return getAll();
    }

    public String get(String key) {
        return settingRepository.findByKey(key)
                .map(SysSetting::getValue)
                .orElseThrow(() -> new BusinessException("设置项不存在: " + key));
    }

    private String maskSecret(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
