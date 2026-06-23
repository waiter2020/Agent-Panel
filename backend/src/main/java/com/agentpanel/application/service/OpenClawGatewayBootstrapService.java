package com.agentpanel.application.service;

import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.config.OpenClawProperties;
import com.agentpanel.runtime.docker.DockerHostDataPathResolver;
import com.agentpanel.runtime.docker.DockerVolumePermissionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawGatewayBootstrapService {

    public static final String OPENCLAW_CONFIG_FILE = "openclaw.json";
    public static final String OPENCLAW_TEMPLATE_CODE = "openclaw";
    public static final String K8S_CONFIG_MAP_SUFFIX = "-openclaw-gateway";

    private final AgentTemplateRepository templateRepository;
    private final AgentRuntimeProperties runtimeProperties;
    private final OpenClawProperties openClawProperties;
    private final DockerHostDataPathResolver dockerHostDataPathResolver;
    private final DockerVolumePermissionService volumePermissionService;
    private final ObjectMapper objectMapper;

    public boolean isOpenClawApplication(Application app) {
        if (app == null || app.getTemplateId() == null) {
            return false;
        }
        return templateRepository.findById(app.getTemplateId())
                .map(AgentTemplate::getCode)
                .filter(OPENCLAW_TEMPLATE_CODE::equals)
                .isPresent();
    }

    public void bootstrapBeforeDeploy(Application app) {
        if (!isOpenClawApplication(app)) {
            return;
        }
        String provider = app.getRuntimeProvider() != null ? app.getRuntimeProvider()
                : runtimeProperties.getProvider();
        if ("docker".equals(provider)) {
            bootstrapDockerVolume(app);
        }
    }

    public String buildBootstrapJson(Application app) {
        return buildBootstrapJson(resolveRuntimeProvider(app));
    }

    public String buildBootstrapJson(String runtimeProvider) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> gateway = new LinkedHashMap<>();
            gateway.put("bind", "lan");
            gateway.put("trustedProxies", openClawProperties.resolveTrustedProxies(runtimeProvider));

            Map<String, Object> trustedProxy = new LinkedHashMap<>();
            trustedProxy.put("userHeader", openClawProperties.getUserHeader());
            trustedProxy.put("requiredHeaders", List.of(openClawProperties.getProxyMarkerHeader()));
            trustedProxy.put("allowUsers", List.of());

            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("mode", "trusted-proxy");
            auth.put("trustedProxy", trustedProxy);
            gateway.put("auth", auth);
            root.put("gateway", gateway);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException("生成 OpenClaw Gateway 配置失败: " + e.getMessage());
        }
    }

    public String mergeGatewayConfig(String existingJson, String runtimeProvider) {
        try {
            Map<String, Object> bootstrap = objectMapper.readValue(
                    buildBootstrapJson(runtimeProvider), new TypeReference<>() {});
            Map<String, Object> merged;
            if (existingJson == null || existingJson.isBlank()) {
                merged = bootstrap;
            } else {
                merged = objectMapper.readValue(existingJson, new TypeReference<>() {});
                deepMerge(merged, bootstrap);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(merged);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("合并 OpenClaw Gateway 配置失败: " + e.getMessage());
        }
    }

    public String k8sConfigMapName(String deployName) {
        return deployName + K8S_CONFIG_MAP_SUFFIX;
    }

    @SuppressWarnings("unchecked")
    static void deepMerge(Map<String, Object> target, Map<String, Object> patch) {
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            String key = entry.getKey();
            Object patchValue = entry.getValue();
            Object targetValue = target.get(key);
            if (patchValue instanceof Map<?, ?> patchMap && targetValue instanceof Map<?, ?> targetMap) {
                deepMerge((Map<String, Object>) targetMap, (Map<String, Object>) patchMap);
            } else {
                target.put(key, patchValue);
            }
        }
    }

    private void bootstrapDockerVolume(Application app) {
        String volumeName = resolveDataVolumeName(app);
        Path volumePath = dockerHostDataPathResolver.toPanelVolumePath(app.getId(), volumeName);
        volumePermissionService.prepareVolumeDirectory(volumePath);
        Path configPath = volumePath.resolve(OPENCLAW_CONFIG_FILE);
        try {
            String existing = Files.exists(configPath)
                    ? Files.readString(configPath, StandardCharsets.UTF_8)
                    : null;
            String merged = mergeGatewayConfig(existing, "docker");
            Files.writeString(configPath, merged, StandardCharsets.UTF_8);
            log.info("OpenClaw gateway config bootstrapped appId={} path={}", app.getId(), configPath);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("写入 OpenClaw 配置失败: " + e.getMessage());
        }
    }

    private String resolveDataVolumeName(Application app) {
        if (app.getVolumes() == null || app.getVolumes().isEmpty()) {
            return "data";
        }
        return String.valueOf(app.getVolumes().getFirst().get("name"));
    }

    private String resolveRuntimeProvider(Application app) {
        return app.getRuntimeProvider() != null ? app.getRuntimeProvider() : runtimeProperties.getProvider();
    }
}
