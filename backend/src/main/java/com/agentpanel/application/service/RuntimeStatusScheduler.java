package com.agentpanel.application.service;

import com.agentpanel.application.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeStatusScheduler {

    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;

    @Scheduled(fixedRate = 30000)
    public void syncStatuses() {
        var apps = applicationRepository.findByDeletedFalseAndRuntimeRefIsNotNull();
        if (apps.isEmpty()) {
            return;
        }
        log.debug("开始同步运行时状态: count={}", apps.size());
        apps.forEach(applicationService::syncRuntimeStatus);
        log.debug("运行时状态同步完成: count={}", apps.size());
    }
}
