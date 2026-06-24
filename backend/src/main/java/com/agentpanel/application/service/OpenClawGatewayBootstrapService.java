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
import java.util.ArrayList;
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
    private final OpenClawTrustedProxyCidrResolver trustedProxyCidrResolver;
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
        bootstrapBeforeDeploy(app, null);
    }

    public void bootstrapBeforeDeploy(Application app, String deployNetworkName) {
        if (!isOpenClawApplication(app)) {
            return;
        }
        String provider = app.getRuntimeProvider() != null ? app.getRuntimeProvider()
                : runtimeProperties.getProvider();
        if ("docker".equals(provider)) {
            bootstrapDockerVolume(app, deployNetworkName);
        }
    }

    public String buildBootstrapJson(Application app) {
        return buildBootstrapJson(resolveRuntimeProvider(app), null);
    }

    public String buildBootstrapJson(String runtimeProvider) {
        return buildBootstrapJson(runtimeProvider, null);
    }

    public String buildBootstrapJson(String runtimeProvider, String deployNetworkName) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> gateway = new LinkedHashMap<>();
            gateway.put("bind", "lan");
            gateway.put("trustedProxies",
                    trustedProxyCidrResolver.resolve(runtimeProvider, deployNetworkName));

            Map<String, Object> trustedProxy = new LinkedHashMap<>();
            trustedProxy.put("userHeader", openClawProperties.getUserHeader());
            trustedProxy.put("requiredHeaders", List.of(openClawProperties.getProxyMarkerHeader()));
            trustedProxy.put("allowUsers", List.of());
            trustedProxy.put("allowLoopback", true);

            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("mode", "trusted-proxy");
            auth.put("trustedProxy", trustedProxy);
            gateway.put("auth", auth);

            Map<String, Object> controlUi = new LinkedHashMap<>();
            controlUi.put("allowedOrigins", openClawProperties.resolvePanelPublicOrigins());
            controlUi.put("dangerouslyAllowHostHeaderOriginFallback", true);
            controlUi.put("allowInsecureAuth", true);
            controlUi.put("dangerouslyDisableDeviceAuth", true);
            gateway.put("controlUi", controlUi);

            root.put("gateway", gateway);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException("生成 OpenClaw Gateway 配置失败: " + e.getMessage());
        }
    }

    public String mergeGatewayConfig(String existingJson, String runtimeProvider) {
        return mergeGatewayConfig(existingJson, runtimeProvider, null);
    }

    public String mergeGatewayConfig(String existingJson, String runtimeProvider, String deployNetworkName) {
        try {
            Map<String, Object> bootstrap = objectMapper.readValue(
                    buildBootstrapJson(runtimeProvider, deployNetworkName), new TypeReference<>() {});
            Map<String, Object> merged;
            if (existingJson == null || existingJson.isBlank()) {
                merged = bootstrap;
            } else {
                merged = objectMapper.readValue(existingJson, new TypeReference<>() {});
                deepMerge(merged, bootstrap);
                applyGatewayTrustedProxiesOverride(merged, bootstrap);
                applyGatewayAuthOverride(merged, bootstrap);
                applyGatewayControlUiOverride(merged, bootstrap);
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
    static void applyGatewayTrustedProxiesOverride(Map<String, Object> merged, Map<String, Object> bootstrap) {
        Object bootstrapGateway = bootstrap.get("gateway");
        if (!(bootstrapGateway instanceof Map<?, ?> bootstrapGatewayMap)) {
            return;
        }
        Object bootstrapTrustedProxies = ((Map<String, Object>) bootstrapGatewayMap).get("trustedProxies");
        if (!(bootstrapTrustedProxies instanceof List<?>)) {
            return;
        }
        Object mergedGateway = merged.get("gateway");
        Map<String, Object> gatewayMap;
        if (mergedGateway instanceof Map<?, ?> existingGatewayMap) {
            gatewayMap = (Map<String, Object>) existingGatewayMap;
        } else {
            gatewayMap = new LinkedHashMap<>();
            merged.put("gateway", gatewayMap);
        }
        gatewayMap.put("trustedProxies", new ArrayList<>((List<String>) bootstrapTrustedProxies));
    }

    @SuppressWarnings("unchecked")
    static void applyGatewayAuthOverride(Map<String, Object> merged, Map<String, Object> bootstrap) {
        Object bootstrapGateway = bootstrap.get("gateway");
        if (!(bootstrapGateway instanceof Map<?, ?> bootstrapGatewayMap)) {
            return;
        }
        Object bootstrapAuth = ((Map<String, Object>) bootstrapGatewayMap).get("auth");
        if (!(bootstrapAuth instanceof Map<?, ?>)) {
            return;
        }
        Object mergedGateway = merged.get("gateway");
        Map<String, Object> gatewayMap;
        if (mergedGateway instanceof Map<?, ?> existingGatewayMap) {
            gatewayMap = (Map<String, Object>) existingGatewayMap;
        } else {
            gatewayMap = new LinkedHashMap<>();
            merged.put("gateway", gatewayMap);
        }
        gatewayMap.put("auth", new LinkedHashMap<>((Map<String, Object>) bootstrapAuth));
    }

    @SuppressWarnings("unchecked")
    static void applyGatewayControlUiOverride(Map<String, Object> merged, Map<String, Object> bootstrap) {
        Object bootstrapGateway = bootstrap.get("gateway");
        if (!(bootstrapGateway instanceof Map<?, ?> bootstrapGatewayMap)) {
            return;
        }
        Object bootstrapControlUi = ((Map<String, Object>) bootstrapGatewayMap).get("controlUi");
        if (!(bootstrapControlUi instanceof Map<?, ?>)) {
            return;
        }
        Object mergedGateway = merged.get("gateway");
        Map<String, Object> gatewayMap;
        if (mergedGateway instanceof Map<?, ?> existingGatewayMap) {
            gatewayMap = (Map<String, Object>) existingGatewayMap;
        } else {
            gatewayMap = new LinkedHashMap<>();
            merged.put("gateway", gatewayMap);
        }
        gatewayMap.put("controlUi", new LinkedHashMap<>((Map<String, Object>) bootstrapControlUi));
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

    private void bootstrapDockerVolume(Application app, String deployNetworkName) {
        String volumeName = resolveDataVolumeName(app);
        Path volumePath = dockerHostDataPathResolver.toPanelVolumePath(app.getId(), volumeName);
        bootstrapDockerVolumeAtPath(app.getId(), volumeName, volumePath, deployNetworkName);
    }

    public void bootstrapDockerVolumeAtPath(Long appId, String volumeName, Path volumePath) {
        bootstrapDockerVolumeAtPath(appId, volumeName, volumePath, null);
    }

    public void bootstrapDockerVolumeAtPath(Long appId, String volumeName, Path volumePath,
                                            String deployNetworkName) {
        volumePermissionService.prepareVolumeDirectory(volumePath);
        Path configPath = volumePath.resolve(OPENCLAW_CONFIG_FILE);
        try {
            String existing = Files.exists(configPath)
                    ? Files.readString(configPath, StandardCharsets.UTF_8)
                    : null;
            String merged = mergeGatewayConfig(existing, "docker", deployNetworkName);
            Files.writeString(configPath, merged, StandardCharsets.UTF_8);
            log.info("OpenClaw gateway config bootstrapped appId={} path={}", appId, configPath);
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
