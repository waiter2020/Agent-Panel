package com.agentpanel.monitor;

import com.agentpanel.application.entity.Application;
import com.agentpanel.application.service.ApplicationService;
import com.agentpanel.common.BusinessException;
import com.agentpanel.runtime.RuntimeProviderFactory;
import com.agentpanel.runtime.api.LogLine;
import com.agentpanel.runtime.api.LogOptions;
import com.agentpanel.runtime.api.ResourceStats;
import com.agentpanel.runtime.api.RuntimeRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final ApplicationService applicationService;
    private final RuntimeProviderFactory runtimeProviderFactory;
    private final com.agentpanel.config.AgentRuntimeProperties runtimeProperties;
    private final StatsRateTracker statsRateTracker;

    public Flux<ResourceStats> statsStream(Long appId) {
        return Flux.interval(Duration.ofSeconds(5))
                .map(tick -> statsRateTracker.enrich(appId, safeGetStats(appId)))
                .onErrorResume(e -> {
                    log.warn("应用 {} 监控流异常: {}", appId, e.getMessage());
                    return Flux.just(ResourceStats.unavailable("监控流异常: " + e.getMessage()));
                });
    }

    private ResourceStats safeGetStats(Long appId) {
        try {
            return applicationService.getStats(appId);
        } catch (BusinessException e) {
            log.warn("应用 {} 监控采集业务异常: {}", appId, e.getMessage());
            return ResourceStats.unavailable(e.getMessage());
        } catch (Exception e) {
            log.warn("应用 {} 监控采集失败: {}", appId, e.getMessage());
            return ResourceStats.unavailable("监控采集失败: " + e.getMessage());
        }
    }

    public Flux<String> logsStream(Long appId, LogOptions options) {
        Application app = applicationService.requireApplication(appId);
        if (app.getRuntimeRef() == null) {
            return Flux.just("应用尚未部署");
        }
        String provider = app.getRuntimeProvider() != null ? app.getRuntimeProvider() : runtimeProperties.getProvider();
        RuntimeRef ref = new RuntimeRef(provider, app.getRuntimeRef(), app.getRuntimeNamespace());
        return runtimeProviderFactory.get(provider).logs(ref, options)
                .map(LogLine::text);
    }

    public byte[] downloadLogs(Long appId, int tail) {
        return logsStream(appId, new LogOptions(false, tail, null))
                .collectList()
                .map(lines -> String.join("\n", lines).getBytes())
                .blockOptional()
                .orElse(new byte[0]);
    }
}
