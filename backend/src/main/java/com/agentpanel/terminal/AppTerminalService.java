package com.agentpanel.terminal;

import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.application.service.ApplicationService;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.RuntimeProviderFactory;
import com.agentpanel.runtime.api.RuntimeRef;
import com.agentpanel.runtime.docker.DockerRuntimeProvider;
import com.agentpanel.runtime.k8s.K8sRuntimeProvider;
import com.agentpanel.system.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppTerminalService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final RuntimeProviderFactory runtimeProviderFactory;
    private final AgentRuntimeProperties runtimeProperties;
    private final AuditService auditService;

    public TerminalSession open(Long appId, TerminalOutputHandler handler) {
        if (!runtimeProperties.isTerminalEnabled()) {
            throw new BusinessException("终端功能已禁用");
        }
        Application app = applicationRepository.findByIdAndDeletedFalse(appId)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        if (app.getRuntimeRef() == null) {
            throw new BusinessException("应用尚未部署，无法打开终端");
        }
        RuntimeRef ref = applicationService.runtimeRef(appId);
        String provider = applicationService.resolveProviderForApp(appId);
        auditService.log(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUsername(),
                "terminal_open",
                "application",
                String.valueOf(appId),
                null,
                null,
                "success");
        return switch (provider) {
            case "docker" -> ((DockerRuntimeProvider) runtimeProviderFactory.get("docker")).openTerminal(ref, handler);
            case "k8s" -> ((K8sRuntimeProvider) runtimeProviderFactory.get("k8s")).openTerminal(ref, handler);
            default -> throw new BusinessException("不支持的运行时 provider: " + provider);
        };
    }
}
