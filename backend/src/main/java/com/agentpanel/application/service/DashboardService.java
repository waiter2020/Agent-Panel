package com.agentpanel.application.service;

import com.agentpanel.application.dto.DashboardStatsDto;
import com.agentpanel.application.dto.PortUsageDto;
import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.entity.AgentTopology;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTemplateRepository;
import com.agentpanel.application.repository.AgentTopologyRepository;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.config.AgentRuntimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ApplicationRepository applicationRepository;
    private final AgentTemplateRepository templateRepository;
    private final AgentTopologyRepository topologyRepository;
    private final AgentRuntimeProperties runtimeProperties;

    public DashboardStatsDto getStats() {
        List<Application> apps = SecurityUtils.isSuperAdmin()
                ? applicationRepository.findByDeletedFalseOrderByUpdatedAtDesc()
                : applicationRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(SecurityUtils.getCurrentTenantId());
        Map<Long, AgentTemplate> templates = new HashMap<>();
        templateRepository.findByDeletedFalse().forEach(t -> templates.put(t.getId(), t));

        DashboardStatsDto dto = new DashboardStatsDto();
        dto.setTotalApps(apps.size());
        dto.setRunningApps((int) apps.stream().filter(a -> "running".equals(a.getStatus())).count());
        dto.setStoppedApps((int) apps.stream().filter(a -> "stopped".equals(a.getStatus())).count());
        dto.setDeployingApps((int) apps.stream().filter(a -> "deploying".equals(a.getStatus())).count());
        dto.setErrorApps((int) apps.stream().filter(a -> "error".equals(a.getStatus())).count());

        Map<String, Integer> byTemplate = new LinkedHashMap<>();
        Map<String, Integer> byRuntime = new LinkedHashMap<>();
        int exposed = 0;
        int internal = 0;
        List<Map<String, Object>> portUsage = new ArrayList<>();
        Set<Integer> seenHostPorts = new HashSet<>();
        int conflicts = 0;

        for (Application app : apps) {
            AgentTemplate template = templates.get(app.getTemplateId());
            String code = template != null ? template.getCode() : "unknown";
            byTemplate.merge(code, 1, Integer::sum);
            String provider = app.getRuntimeProvider() != null ? app.getRuntimeProvider() : runtimeProperties.getProvider();
            byRuntime.merge(provider, 1, Integer::sum);

            if (app.getPorts() != null) {
                for (Map<String, Object> port : app.getPorts()) {
                    boolean expose = Boolean.TRUE.equals(port.get("expose"));
                    if (expose) {
                        exposed++;
                    } else {
                        internal++;
                    }
                    Object hostPortObj = port.get("hostPort");
                    if (hostPortObj instanceof Number hostPort) {
                        if (!seenHostPorts.add(hostPort.intValue())) {
                            conflicts++;
                        }
                    }
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("appId", app.getId());
                    entry.put("appName", app.getName());
                    entry.put("templateCode", code);
                    entry.put("portName", port.get("name"));
                    entry.put("containerPort", port.get("containerPort"));
                    entry.put("hostPort", hostPortObj);
                    entry.put("expose", expose);
                    if (expose && hostPortObj != null) {
                        entry.put("accessUrl", resolveAccessHost(app) + ":" + hostPortObj);
                    }
                    portUsage.add(entry);
                }
            }
        }

        dto.setByTemplate(byTemplate);
        dto.setByRuntime(byRuntime);
        dto.setExposedPorts(exposed);
        dto.setInternalPorts(internal);
        dto.setPortConflicts(conflicts);
        dto.setPortUsage(portUsage);

        List<AgentTopology> topologies = SecurityUtils.isSuperAdmin()
                ? topologyRepository.findByDeletedFalseOrderByUpdatedAtDesc()
                : topologyRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(SecurityUtils.getCurrentTenantId());
        dto.setTopologyCount(topologies.size());
        dto.setDeployedTopologies((int) topologies.stream().filter(t -> "deployed".equals(t.getStatus())).count());

        dto.setRecentApps(apps.stream().limit(8).map(app -> {
            AgentTemplate template = templates.get(app.getTemplateId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", app.getId());
            item.put("name", app.getName());
            item.put("status", app.getStatus());
            item.put("templateCode", template != null ? template.getCode() : null);
            item.put("templateName", template != null ? template.getName() : null);
            return item;
        }).toList());

        return dto;
    }

    public List<PortUsageDto> listPortUsage() {
        return getStats().getPortUsage().stream().map(entry -> {
            PortUsageDto dto = new PortUsageDto();
            dto.setAppId(((Number) entry.get("appId")).longValue());
            dto.setAppName(String.valueOf(entry.get("appName")));
            dto.setTemplateCode(entry.get("templateCode") != null ? String.valueOf(entry.get("templateCode")) : null);
            dto.setPortName(String.valueOf(entry.get("portName")));
            if (entry.get("containerPort") instanceof Number n) {
                dto.setContainerPort(n.intValue());
            }
            if (entry.get("hostPort") instanceof Number n) {
                dto.setHostPort(n.intValue());
            }
            dto.setExpose(Boolean.TRUE.equals(entry.get("expose")));
            dto.setAccessUrl(entry.get("accessUrl") != null ? String.valueOf(entry.get("accessUrl")) : null);
            return dto;
        }).toList();
    }

    private String resolveAccessHost(Application app) {
        if ("k8s".equals(app.getRuntimeProvider() != null ? app.getRuntimeProvider() : runtimeProperties.getProvider())) {
            return runtimeProperties.getK8s().getAccessHost();
        }
        return runtimeProperties.getDocker().getAccessHost();
    }
}
