package com.agentpanel.application.service;

import com.agentpanel.application.dto.*;
import com.agentpanel.application.entity.*;
import com.agentpanel.application.repository.*;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.auth.repository.ApiKeyRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.CryptoService;
import com.agentpanel.common.TenantAccessHelper;
import com.agentpanel.config.AgentPanelProperties;
import com.agentpanel.auth.dto.CreateApiKeyRequest;
import com.agentpanel.auth.dto.CreateApiKeyResponse;
import com.agentpanel.auth.ApiKeyManagementService;
import com.agentpanel.application.dto.TopologyDto;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.registry.ImageReference;
import com.agentpanel.runtime.RuntimeProviderFactory;
import com.agentpanel.runtime.docker.DockerRuntimeProvider;
import com.agentpanel.runtime.docker.DockerHostDataPathResolver;
import com.agentpanel.runtime.api.*;
import com.agentpanel.memory.entity.SharedSkill;
import com.agentpanel.memory.repository.SharedSkillRepository;
import com.agentpanel.system.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final AgentTemplateRepository templateRepository;
    private final AppEnvRepository appEnvRepository;
    private final AppDeploymentRepository deploymentRepository;
    private final AgentTopologyRepository topologyRepository;
    private final AgentTopologyNodeRepository topologyNodeRepository;
    private final AgentLinkRepository linkRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final PeerUrlResolver peerUrlResolver;
    private final RuntimeProviderFactory runtimeProviderFactory;
    private final AgentRuntimeProperties runtimeProperties;
    private final AgentPanelProperties panelProperties;
    private final ApiKeyManagementService apiKeyManagementService;
    private final CryptoService cryptoService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SharedSkillRepository sharedSkillRepository;
    private final DockerHostDataPathResolver dockerHostDataPathResolver;
    private final TaskKanbanService taskKanbanService;
    private final OpenClawGatewayBootstrapService openClawGatewayBootstrapService;

    public List<AgentTemplate> listTemplates() {
        return templateRepository.findByDeletedFalse();
    }

    public List<ApplicationDto> listApps() {
        List<Application> apps = SecurityUtils.isSuperAdmin()
                ? applicationRepository.findByDeletedFalseOrderByUpdatedAtDesc()
                : applicationRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(SecurityUtils.getCurrentTenantId());
        return apps.stream()
                .map(this::toDto)
                .toList();
    }

    public ApplicationDto get(Long id) {
        return toDto(findApp(id));
    }

    public Application requireApplication(Long id) {
        return findApp(id);
    }

    @Transactional
    public ApplicationDto create(ApplicationDto dto, HttpServletRequest request) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        if (applicationRepository.existsByNameAndTenantIdAndDeletedFalse(dto.getName(), tenantId)) {
            throw new BusinessException("应用名称已存在");
        }
        AgentTemplate template = templateRepository.findById(dto.getTemplateId())
                .orElseThrow(() -> new BusinessException("模板不存在"));
        String image = dto.getImage() != null && !dto.getImage().isBlank() ? dto.getImage() : template.getImage();
        ImageReference.validateImageFormat(image);
        Application app = new Application();
        app.setName(dto.getName());
        app.setTemplateId(template.getId());
        app.setOwnerId(requireCurrentUserId());
        app.setImage(image);
        app.setTag(dto.getTag() != null ? dto.getTag() : template.getDefaultTag());
        app.setStatus("created");
        app.setPorts(dto.getPorts() != null ? dto.getPorts() : template.getPortSchema());
        app.setResources(dto.getResources() != null ? dto.getResources() : template.getDefaultResources());
        app.setVolumes(dto.getVolumes() != null ? dto.getVolumes() : template.getVolumeSchema());
        app.setReplicas(dto.getReplicas() > 0 ? dto.getReplicas() : 1);
        app.setRuntimeProvider(dto.getRuntimeProvider());
        app.setRemark(dto.getRemark());
        app.setCreatedBy(requireCurrentUserId());
        app.setTenantId(SecurityUtils.getCurrentTenantId());
        Application saved = applicationRepository.save(app);
        saveEnv(saved.getId(), dto.getEnv());
        audit("create", saved.getId(), request);
        return toDto(saved);
    }

    @Transactional
    public ApplicationDto update(Long id, ApplicationDto dto, HttpServletRequest request) {
        Application app = findApp(id);
        app.setRemark(dto.getRemark());
        if (dto.getImage() != null && !dto.getImage().isBlank()) {
            ImageReference.validateImageFormat(dto.getImage());
            app.setImage(dto.getImage());
        }
        if (dto.getTag() != null && !dto.getTag().isBlank()) {
            app.setTag(dto.getTag());
        }
        if (dto.getReplicas() > 0) {
            app.setReplicas(dto.getReplicas());
        }
        if (dto.getRuntimeProvider() != null && !dto.getRuntimeProvider().isBlank()) {
            app.setRuntimeProvider(dto.getRuntimeProvider());
        }
        if (dto.getPorts() != null) app.setPorts(dto.getPorts());
        if (dto.getResources() != null) app.setResources(dto.getResources());
        if (dto.getVolumes() != null) app.setVolumes(dto.getVolumes());
        if (dto.getEnv() != null) {
            mergeEnvUpdate(id, dto.getEnv());
        }
        Application saved = applicationRepository.save(app);
        audit("update", saved.getId(), request);
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest request) {
        Application app = findApp(id);
        if (app.getRuntimeRef() != null) {
            try {
                runtimeProviderFactory.get(resolveProvider(app)).remove(ref(app));
            } catch (Exception ignored) {
            }
        }
        app.setDeleted(true);
        applicationRepository.save(app);
        syncKanbanForApp(app);
        audit("delete", id, request);
    }

    @Transactional
    public TopologyDto deployTopology(Long topologyId, HttpServletRequest request) {
        AgentTopology topology = topologyRepository.findByIdAndDeletedFalse(topologyId)
                .orElseThrow(() -> new BusinessException("拓扑不存在"));
        TenantAccessHelper.requireOwnedTenant(topology.getTenantId(), "拓扑不存在");
        List<AgentTopologyNode> nodes = topologyNodeRepository.findByTopologyId(topologyId);
        if (nodes.isEmpty()) {
            throw new BusinessException("拓扑中没有成员应用");
        }
        topology.setStatus("deploying");
        topologyRepository.save(topology);

        boolean newlyCreatedKey = topology.getInferenceApiKeyId() == null;
        String inferenceRawKey = ensureTopologyInferenceKey(topology);
        String networkName = topology.getNetworkName();
        ensureDockerNetwork(networkName);

        List<InjectedEnvDto> injectedEnv = injectPeerEnvFromLinks(topologyId, nodes);
        injectedEnv.addAll(injectSharedSkillsEnv(topologyId, nodes));
        injectedEnv.addAll(injectAgentPanelIntegrationEnv(nodes, inferenceRawKey));

        for (AgentTopologyNode node : nodes) {
            Application app = findApp(node.getApplicationId());
            injectInferenceEnv(app.getId(), inferenceRawKey);
            deployWithNetwork(app.getId(), networkName, request);
            connectToNetworkIfNeeded(app, networkName);
        }

        topology.setStatus("deployed");
        topologyRepository.save(topology);
        auditService.log(requireCurrentUserId(), SecurityUtils.getCurrentUsername(),
                "deploy", "topology", String.valueOf(topologyId), null, request.getRemoteAddr(), "success");

        TopologyDto dto = buildTopologyDto(topology, nodes);
        if (newlyCreatedKey && inferenceRawKey != null) {
            dto.setInferenceKeyRaw(inferenceRawKey);
        }
        dto.setInjectedEnv(injectedEnv);
        dto.setInjectedSkills(buildInjectedSkillsSummary(topologyId));
        dto.setMemberAccessUrls(buildMemberAccessUrls(nodes));
        dto.setNeedsKeyRedeploy(false);
        return dto;
    }

    @Transactional
    public TopologyDto redeployTopology(Long topologyId, HttpServletRequest request) {
        AgentTopology topology = topologyRepository.findByIdAndDeletedFalse(topologyId)
                .orElseThrow(() -> new BusinessException("拓扑不存在"));
        TenantAccessHelper.requireOwnedTenant(topology.getTenantId(), "拓扑不存在");
        if (topology.getInferenceApiKeyId() != null) {
            alignTopologyInferenceKeyTenant(topology);
            apiKeyRepository.findById(topology.getInferenceApiKeyId()).ifPresent(key -> {
                if (key.isDeprecated()) {
                    String newKey = rotateTopologyInferenceKey(topology);
                    injectInferenceEnvToAllNodes(topologyId, newKey);
                }
            });
        }
        return deployTopology(topologyId, request);
    }

    private TopologyDto buildTopologyDto(AgentTopology topology, List<AgentTopologyNode> nodes) {
        TopologyDto dto = new TopologyDto();
        dto.setId(topology.getId());
        dto.setName(topology.getName());
        dto.setDescription(topology.getDescription());
        dto.setNetworkName(topology.getNetworkName());
        dto.setStatus(topology.getStatus());
        dto.setOwnerId(topology.getOwnerId());
        dto.setInferenceApiKeyId(topology.getInferenceApiKeyId());
        dto.setCreatedAt(topology.getCreatedAt());
        dto.setUpdatedAt(topology.getUpdatedAt());
        dto.setNodes(nodes.stream().map(node -> {
            TopologyDto.TopologyNodeDto nodeDto = new TopologyDto.TopologyNodeDto();
            nodeDto.setId(node.getId());
            nodeDto.setApplicationId(node.getApplicationId());
            nodeDto.setRole(node.getRole());
            nodeDto.setConfig(node.getConfig());
            Application app = findApp(node.getApplicationId());
            nodeDto.setApplicationName(app.getName());
            nodeDto.setApplicationStatus(app.getStatus());
            return nodeDto;
        }).toList());
        return dto;
    }

    private List<InjectedEnvDto> injectPeerEnvFromLinks(Long topologyId, List<AgentTopologyNode> nodes) {
        Map<Long, AgentTopologyNode> nodeById = nodes.stream()
                .collect(Collectors.toMap(AgentTopologyNode::getId, n -> n, (a, b) -> a));
        List<InjectedEnvDto> injected = new ArrayList<>();
        for (AgentLink link : linkRepository.findByTopologyId(topologyId)) {
            AgentTopologyNode fromNode = nodeById.get(link.getFromNodeId());
            AgentTopologyNode toNode = nodeById.get(link.getToNodeId());
            if (fromNode == null || toNode == null) {
                continue;
            }
            Application fromApp = findApp(fromNode.getApplicationId());
            Application toApp = findApp(toNode.getApplicationId());
            String templateCode = templateRepository.findById(toApp.getTemplateId())
                    .map(AgentTemplate::getCode)
                    .orElse("peer");
            String peerUrl = "mcp".equals(link.getProtocol())
                    ? peerUrlResolver.resolveMcpPeerUrl(toApp)
                    : peerUrlResolver.resolveHttpPeerUrl(toApp);
            String envKey = peerUrlResolver.resolveEnvKeyFromConfig(link.getConfig(), templateCode, link.getProtocol());
            upsertEnv(fromNode.getApplicationId(), envKey, peerUrl, false);
            InjectedEnvDto item = new InjectedEnvDto();
            item.setApplicationId(fromNode.getApplicationId());
            item.setApplicationName(fromApp.getName());
            item.setEnvKey(envKey);
            item.setEnvValue(peerUrl);
            item.setSource("link:" + link.getProtocol() + "→" + toApp.getName());
            injected.add(item);
        }
        return injected;
    }

    public List<MemberAccessUrlDto> buildMemberAccessUrlsForTopology(List<AgentTopologyNode> nodes) {
        return buildMemberAccessUrls(nodes);
    }

    public List<InjectedEnvDto> previewInjectedEnv(Long topologyId, List<AgentTopologyNode> nodes) {
        Map<Long, AgentTopologyNode> nodeById = nodes.stream()
                .collect(Collectors.toMap(AgentTopologyNode::getId, n -> n, (a, b) -> a));
        List<InjectedEnvDto> preview = new ArrayList<>();
        for (AgentLink link : linkRepository.findByTopologyId(topologyId)) {
            AgentTopologyNode fromNode = nodeById.get(link.getFromNodeId());
            AgentTopologyNode toNode = nodeById.get(link.getToNodeId());
            if (fromNode == null || toNode == null) {
                continue;
            }
            Application fromApp = findApp(fromNode.getApplicationId());
            Application toApp = findApp(toNode.getApplicationId());
            String templateCode = templateRepository.findById(toApp.getTemplateId())
                    .map(AgentTemplate::getCode)
                    .orElse("peer");
            String peerUrl = "mcp".equals(link.getProtocol())
                    ? peerUrlResolver.resolveMcpPeerUrl(toApp)
                    : peerUrlResolver.resolveHttpPeerUrl(toApp);
            String envKey = peerUrlResolver.resolveEnvKeyFromConfig(link.getConfig(), templateCode, link.getProtocol());
            InjectedEnvDto item = new InjectedEnvDto();
            item.setApplicationId(fromNode.getApplicationId());
            item.setApplicationName(fromApp.getName());
            item.setEnvKey(envKey);
            item.setEnvValue(peerUrl);
            item.setSource("link:" + link.getProtocol() + "→" + toApp.getName());
            preview.add(item);
        }
        return preview;
    }

    public List<InjectedEnvDto> previewSkillEnv(Long topologyId, List<AgentTopologyNode> nodes) {
        List<SharedSkill> skills = sharedSkillRepository.findByTopologyIdOrderByNameAsc(topologyId);
        String skillsApiUrl = panelApiBase() + "/api/skills?topologyId=" + topologyId;
        String skillsJson = buildSkillsJson(skills);
        List<InjectedEnvDto> preview = new ArrayList<>();
        for (AgentTopologyNode node : nodes) {
            Application app = findApp(node.getApplicationId());
            for (String envKey : List.of("AGENTPANEL_SKILLS_API", "SHARED_SKILLS_JSON")) {
                InjectedEnvDto item = new InjectedEnvDto();
                item.setApplicationId(node.getApplicationId());
                item.setApplicationName(app.getName());
                item.setEnvKey(envKey);
                item.setEnvValue("AGENTPANEL_SKILLS_API".equals(envKey) ? skillsApiUrl : skillsJson);
                item.setSource("skills");
                preview.add(item);
            }
        }
        return preview;
    }

    public List<InjectedSkillDto> buildInjectedSkillsSummary(Long topologyId) {
        return sharedSkillRepository.findByTopologyIdOrderByNameAsc(topologyId).stream()
                .map(skill -> {
                    InjectedSkillDto dto = new InjectedSkillDto();
                    dto.setId(skill.getId());
                    dto.setName(skill.getName());
                    dto.setFilePath(skill.getFilePath());
                    return dto;
                })
                .toList();
    }

    private List<InjectedEnvDto> injectSharedSkillsEnv(Long topologyId, List<AgentTopologyNode> nodes) {
        List<SharedSkill> skills = sharedSkillRepository.findByTopologyIdOrderByNameAsc(topologyId);
        String skillsApiUrl = panelApiBase() + "/api/skills?topologyId=" + topologyId;
        String skillsJson = buildSkillsJson(skills);
        List<InjectedEnvDto> injected = new ArrayList<>();
        for (AgentTopologyNode node : nodes) {
            Application app = findApp(node.getApplicationId());
            upsertEnv(node.getApplicationId(), "AGENTPANEL_SKILLS_API", skillsApiUrl, false);
            upsertEnv(node.getApplicationId(), "SHARED_SKILLS_JSON", skillsJson, false);
            for (String envKey : List.of("AGENTPANEL_SKILLS_API", "SHARED_SKILLS_JSON")) {
                InjectedEnvDto item = new InjectedEnvDto();
                item.setApplicationId(node.getApplicationId());
                item.setApplicationName(app.getName());
                item.setEnvKey(envKey);
                item.setEnvValue("AGENTPANEL_SKILLS_API".equals(envKey) ? skillsApiUrl : skillsJson);
                item.setSource("skills");
                injected.add(item);
            }
        }
        return injected;
    }

    public List<InjectedEnvDto> previewAgentPanelIntegrationEnv(List<AgentTopologyNode> nodes) {
        String base = panelApiBase();
        String webhook = base + "/api/delegations/webhook";
        String memoryApi = base + "/api/memory";
        List<InjectedEnvDto> preview = new ArrayList<>();
        for (AgentTopologyNode node : nodes) {
            Application app = findApp(node.getApplicationId());
            for (String envKey : List.of(
                    "AGENTPANEL_DELEGATION_WEBHOOK",
                    "AGENTPANEL_MEMORY_API",
                    "AGENTPANEL_API_KEY")) {
                InjectedEnvDto item = new InjectedEnvDto();
                item.setApplicationId(node.getApplicationId());
                item.setApplicationName(app.getName());
                item.setEnvKey(envKey);
                item.setEnvValue(switch (envKey) {
                    case "AGENTPANEL_DELEGATION_WEBHOOK" -> webhook;
                    case "AGENTPANEL_MEMORY_API" -> memoryApi;
                    default -> "<topology-inference-key>";
                });
                item.setSource("agent-panel");
                preview.add(item);
            }
        }
        return preview;
    }

    private List<InjectedEnvDto> injectAgentPanelIntegrationEnv(List<AgentTopologyNode> nodes, String apiKey) {
        String base = panelApiBase();
        String webhook = base + "/api/delegations/webhook";
        String memoryApi = base + "/api/memory";
        List<InjectedEnvDto> injected = new ArrayList<>();
        for (AgentTopologyNode node : nodes) {
            Application app = findApp(node.getApplicationId());
            upsertEnv(node.getApplicationId(), "AGENTPANEL_DELEGATION_WEBHOOK", webhook, false);
            upsertEnv(node.getApplicationId(), "AGENTPANEL_MEMORY_API", memoryApi, false);
            if (apiKey != null && !apiKey.isBlank()) {
                upsertEnv(node.getApplicationId(), "AGENTPANEL_API_KEY", apiKey, true);
            }
            for (String envKey : List.of(
                    "AGENTPANEL_DELEGATION_WEBHOOK",
                    "AGENTPANEL_MEMORY_API",
                    "AGENTPANEL_API_KEY")) {
                InjectedEnvDto item = new InjectedEnvDto();
                item.setApplicationId(node.getApplicationId());
                item.setApplicationName(app.getName());
                item.setEnvKey(envKey);
                item.setEnvValue(switch (envKey) {
                    case "AGENTPANEL_DELEGATION_WEBHOOK" -> webhook;
                    case "AGENTPANEL_MEMORY_API" -> memoryApi;
                    default -> apiKey != null ? apiKey : "";
                });
                item.setSource("agent-panel");
                injected.add(item);
            }
        }
        return injected;
    }

    private String buildSkillsJson(List<SharedSkill> skills) {
        try {
            List<Map<String, Object>> entries = skills.stream()
                    .map(skill -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("id", skill.getId());
                        entry.put("name", skill.getName());
                        if (skill.getFilePath() != null && !skill.getFilePath().isBlank()) {
                            entry.put("filePath", skill.getFilePath());
                        }
                        return entry;
                    })
                    .toList();
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            log.warn("序列化共享技能 JSON 失败: {}", e.getMessage());
            return "[]";
        }
    }

    private String panelApiBase() {
        String url = panelProperties.getInferenceUrl();
        if (url.endsWith("/v1/")) {
            return url.substring(0, url.length() - 4);
        }
        if (url.endsWith("/v1")) {
            return url.substring(0, url.length() - 3);
        }
        return url;
    }

    private List<MemberAccessUrlDto> buildMemberAccessUrls(List<AgentTopologyNode> nodes) {
        List<MemberAccessUrlDto> urls = new ArrayList<>();
        for (AgentTopologyNode node : nodes) {
            Application app = findApp(node.getApplicationId());
            String peerUrl = peerUrlResolver.resolveHttpPeerUrl(app);
            for (Map<String, Object> accessUrl : buildAccessUrls(app)) {
                MemberAccessUrlDto item = new MemberAccessUrlDto();
                item.setApplicationId(app.getId());
                item.setApplicationName(app.getName());
                item.setRole(node.getRole());
                item.setName(String.valueOf(accessUrl.get("name")));
                item.setUrl(String.valueOf(accessUrl.get("url")));
                item.setPeerUrl(peerUrl);
                urls.add(item);
            }
            if (buildAccessUrls(app).isEmpty()) {
                MemberAccessUrlDto item = new MemberAccessUrlDto();
                item.setApplicationId(app.getId());
                item.setApplicationName(app.getName());
                item.setRole(node.getRole());
                item.setName("internal");
                item.setUrl(peerUrl);
                item.setPeerUrl(peerUrl);
                urls.add(item);
            }
        }
        return urls;
    }

    private String rotateTopologyInferenceKey(AgentTopology topology) {
        CreateApiKeyResponse rotated = apiKeyManagementService.rotate(topology.getInferenceApiKeyId());
        topology.setInferenceApiKeyId(rotated.getId());
        topologyRepository.save(topology);
        return rotated.getRawKey();
    }

    private void injectInferenceEnvToAllNodes(Long topologyId, String inferenceRawKey) {
        for (AgentTopologyNode node : topologyNodeRepository.findByTopologyId(topologyId)) {
            injectInferenceEnv(node.getApplicationId(), inferenceRawKey);
        }
    }

    @Transactional
    public ApplicationDto deploy(Long id, HttpServletRequest request) {
        Application app = findApp(id);
        app.setStatus("deploying");
        applicationRepository.save(app);
        syncKanbanForApp(app);
        try {
            openClawGatewayBootstrapService.bootstrapBeforeDeploy(app, null);
            DeploySpec spec = buildDeploySpec(app, null, null);
            AgentRuntimeProvider provider = runtimeProviderFactory.get(resolveProvider(app));
            DeployResult result = provider.deploy(spec);
            app.setRuntimeRef(result.ref().ref());
            app.setRuntimeNamespace(result.ref().namespace());
            app.setStatus(mapDeployStatus(result.status()));
            if (!result.resolvedPorts().isEmpty()) {
                persistResolvedPorts(app, result.resolvedPorts());
            }
            applicationRepository.save(app);
            AppDeployment deployment = new AppDeployment();
            deployment.setApplicationId(app.getId());
            deployment.setProvider(result.ref().provider());
            deployment.setRef(result.ref().ref());
            deployment.setNamespace(result.ref().namespace());
            deployment.setImageUsed(spec.fullImage());
            deployment.setStatus(result.status());
            deployment.setMessage(result.message());
            deployment.setStartedAt(Instant.now());
            deployment.setSpecSnapshot(objectMapper.convertValue(spec, Map.class));
            deploymentRepository.save(deployment);
            audit("deploy", id, request);
            syncKanbanForApp(app);
            return toDto(app);
        } catch (Exception e) {
            markDeployFailed(app, e);
            throw e instanceof BusinessException ? (BusinessException) e
                    : new BusinessException("部署失败: " + e.getMessage());
        }
    }

    public ApplicationDto start(Long id, HttpServletRequest request) {
        Application app = findApp(id);
        runtimeProviderFactory.get(resolveProvider(app)).start(ref(app));
        app.setStatus("running");
        applicationRepository.save(app);
        syncKanbanForApp(app);
        audit("start", id, request);
        return toDto(app);
    }

    public ApplicationDto stop(Long id, HttpServletRequest request) {
        Application app = findApp(id);
        runtimeProviderFactory.get(resolveProvider(app)).stop(ref(app));
        app.setStatus("stopped");
        applicationRepository.save(app);
        syncKanbanForApp(app);
        audit("stop", id, request);
        return toDto(app);
    }

    public ApplicationDto restart(Long id, HttpServletRequest request) {
        Application app = findApp(id);
        runtimeProviderFactory.get(resolveProvider(app)).restart(ref(app));
        app.setStatus("running");
        applicationRepository.save(app);
        syncKanbanForApp(app);
        audit("restart", id, request);
        return toDto(app);
    }

    public void syncRuntimeStatus(Application app) {
        if (app.getRuntimeRef() == null) {
            return;
        }
        try {
            AgentRuntimeProvider provider = runtimeProviderFactory.get(resolveProvider(app));
            RuntimeStatus status = provider.status(ref(app));
            if (DockerRuntimeProvider.isContainerMissingStatus(status)) {
                reconcileMissingRuntimeRef(app, provider);
                return;
            }
            String newStatus = mapRuntimePhase(status.phase());
            if (!newStatus.equals(app.getStatus())) {
                app.setStatus(newStatus);
                applicationRepository.save(app);
                syncKanbanForApp(app);
            }
        } catch (Exception e) {
            log.warn("同步应用 {} 运行时状态失败: {}", app.getId(), e.getMessage());
        }
    }

    public RuntimeStatus getStatus(Long id) {
        Application app = findApp(id);
        if (app.getRuntimeRef() == null) {
            return new RuntimeStatus(RuntimeStatus.Phase.CREATED, false, "未部署");
        }
        RuntimeStatus status = runtimeProviderFactory.get(resolveProvider(app)).status(ref(app));
        if (DockerRuntimeProvider.isContainerMissingStatus(status)) {
            return new RuntimeStatus(RuntimeStatus.Phase.MISSING, false, "容器不存在，请重新部署");
        }
        return status;
    }

    public ResourceStats getStats(Long id) {
        Application app = findApp(id);
        if (app.getRuntimeRef() == null) {
            return ResourceStats.unavailable("应用尚未部署");
        }
        try {
            ResourceStats stats = runtimeProviderFactory.get(resolveProvider(app)).stats(ref(app));
            if (!stats.available() && stats.message() != null) {
                log.warn("应用 {} 监控不可用: {}", id, stats.message());
            }
            return stats;
        } catch (IllegalArgumentException e) {
            log.warn("应用 {} 监控失败，运行时 provider 不支持: {}", id, e.getMessage());
            return ResourceStats.unavailable("运行时 provider 不支持: " + e.getMessage());
        } catch (Exception e) {
            log.warn("应用 {} 监控采集失败: {}", id, e.getMessage());
            return ResourceStats.unavailable("监控采集失败: " + e.getMessage());
        }
    }

    public AppSkillsContextDto getAppSkillsContext(Long appId) {
        findApp(appId);
        AppSkillsContextDto context = new AppSkillsContextDto();
        List<AgentTopologyNode> nodes = topologyNodeRepository.findByApplicationId(appId);
        if (nodes.isEmpty()) {
            context.setSkills(List.of());
            context.setInjectedEnv(List.of());
            return context;
        }
        Long topologyId = nodes.getFirst().getTopologyId();
        context.setTopologyId(topologyId);
        topologyRepository.findByIdAndDeletedFalse(topologyId)
                .ifPresent(t -> context.setTopologyName(t.getName()));
        context.setSkills(sharedSkillRepository.findByTopologyIdOrderByNameAsc(topologyId).stream()
                .filter(skill -> skill.getApplicationId() == null || appId.equals(skill.getApplicationId()))
                .map(skill -> {
                    com.agentpanel.memory.dto.SharedSkillDto dto = new com.agentpanel.memory.dto.SharedSkillDto();
                    dto.setId(skill.getId());
                    dto.setTopologyId(skill.getTopologyId());
                    dto.setApplicationId(skill.getApplicationId());
                    dto.setName(skill.getName());
                    dto.setDescription(skill.getDescription());
                    dto.setFilePath(skill.getFilePath());
                    dto.setCreatedAt(skill.getCreatedAt());
                    dto.setUpdatedAt(skill.getUpdatedAt());
                    return dto;
                })
                .toList());
        context.setInjectedEnv(previewSkillEnv(topologyId, nodes));
        return context;
    }

    public DeploySpec buildDeploySpec(Application app) {
        return buildDeploySpec(app, null, null);
    }

    public DeploySpec buildDeploySpec(Application app, String network, String namespace) {
        AgentTemplate template = templateRepository.findById(app.getTemplateId())
                .orElseThrow(() -> new BusinessException("模板不存在"));
        Map<String, String> env = new LinkedHashMap<>();
        Map<String, String> secretEnv = new LinkedHashMap<>();
        appEnvRepository.findByApplicationId(app.getId()).forEach(e -> {
            String value = e.isSecret() ? cryptoService.decrypt(e.getValue()) : e.getValue();
            if (e.isSecret()) {
                secretEnv.put(e.getKey(), value);
            } else {
                env.put(e.getKey(), value);
            }
        });
        applyTemplateEnvDefaults(env, secretEnv, template);
        List<PortMapping> ports = app.getPorts().stream().map(p -> new PortMapping(
                String.valueOf(p.get("name")),
                ((Number) p.get("containerPort")).intValue(),
                p.get("hostPort") != null ? ((Number) p.get("hostPort")).intValue() : null,
                String.valueOf(p.getOrDefault("protocol", "TCP")),
                Boolean.TRUE.equals(p.get("expose"))
        )).toList();
        List<VolumeMount> volumes = app.getVolumes().stream().map(v -> {
            String name = String.valueOf(v.get("name"));
            String containerPath = String.valueOf(v.get("containerPath"));
            String hostPath = "docker".equals(resolveProvider(app))
                    ? dockerHostDataPathResolver.toHostVolumePath(app.getId(), name)
                    : Path.of(runtimeProperties.getDocker().getDataRoot(), String.valueOf(app.getId()), name).toString();
            return new VolumeMount(name, containerPath, hostPath);
        }).toList();
        Map<String, Object> resources = app.getResources() != null ? app.getResources() : template.getDefaultResources();
        ResourceLimits limits = new ResourceLimits(
                String.valueOf(resources.getOrDefault("cpu", "1")),
                String.valueOf(resources.getOrDefault("memory", "1Gi"))
        );
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("agentpanel.app", String.valueOf(app.getId()));
        templateRepository.findById(app.getTemplateId()).ifPresent(t -> labels.put("agentpanel.template", t.getCode()));
        String containerName = "app-" + app.getId();
        boolean exposeViaIngress = "k8s".equals(resolveProvider(app))
                && runtimeProperties.getK8s().isExposeViaIngress();
        return new DeploySpec(containerName, app.getImage(), app.getTag(), env, secretEnv, ports, volumes, limits,
                app.getReplicas(), labels, network, namespace, exposeViaIngress);
    }

    private ApplicationDto deployWithNetwork(Long id, String network, HttpServletRequest request) {
        Application app = findApp(id);
        app.setStatus("deploying");
        applicationRepository.save(app);
        syncKanbanForApp(app);
        try {
            openClawGatewayBootstrapService.bootstrapBeforeDeploy(app, network);
            DeploySpec spec = buildDeploySpec(app, network, null);
            AgentRuntimeProvider provider = runtimeProviderFactory.get(resolveProvider(app));
            DeployResult result = provider.deploy(spec);
            app.setRuntimeRef(result.ref().ref());
            app.setRuntimeNamespace(result.ref().namespace());
            app.setStatus(mapDeployStatus(result.status()));
            if (!result.resolvedPorts().isEmpty()) {
                persistResolvedPorts(app, result.resolvedPorts());
            }
            applicationRepository.save(app);
            AppDeployment deployment = new AppDeployment();
            deployment.setApplicationId(app.getId());
            deployment.setProvider(result.ref().provider());
            deployment.setRef(result.ref().ref());
            deployment.setNamespace(result.ref().namespace());
            deployment.setImageUsed(spec.fullImage());
            deployment.setStatus(result.status());
            deployment.setMessage(result.message());
            deployment.setStartedAt(Instant.now());
            deployment.setSpecSnapshot(objectMapper.convertValue(spec, Map.class));
            deploymentRepository.save(deployment);
            audit("deploy", id, request);
            syncKanbanForApp(app);
            return toDto(app);
        } catch (Exception e) {
            markDeployFailed(app, e);
            throw e instanceof BusinessException ? (BusinessException) e
                    : new BusinessException("部署失败: " + e.getMessage());
        }
    }

    private void markDeployFailed(Application app, Exception e) {
        app.setStatus("error");
        app.setRuntimeRef(null);
        app.setRuntimeNamespace(null);
        applicationRepository.save(app);
        syncKanbanForApp(app);
        log.warn("应用 {} 部署失败，已清理 runtimeRef: {}", app.getId(), e.getMessage());
    }

    private void reconcileMissingRuntimeRef(Application app, AgentRuntimeProvider provider) {
        if ("docker".equals(resolveProvider(app)) && provider instanceof DockerRuntimeProvider dockerProvider) {
            Optional<String> found = dockerProvider.findContainerIdByName("app-" + app.getId());
            if (found.isPresent()) {
                log.info("应用 {} runtimeRef 已自愈重绑: {}", app.getId(), found.get());
                app.setRuntimeRef(found.get());
                applicationRepository.save(app);
                RuntimeStatus status = provider.status(ref(app));
                if (!DockerRuntimeProvider.isContainerMissingStatus(status)) {
                    app.setStatus(mapRuntimePhase(status.phase()));
                    applicationRepository.save(app);
                    syncKanbanForApp(app);
                }
                return;
            }
        }
        log.warn("应用 {} 容器不存在，清理陈旧 runtimeRef", app.getId());
        app.setRuntimeRef(null);
        app.setRuntimeNamespace(null);
        app.setStatus("error");
        applicationRepository.save(app);
        syncKanbanForApp(app);
    }

    private String ensureTopologyInferenceKey(AgentTopology topology) {
        if (topology.getInferenceApiKeyId() != null) {
            return findExistingInferenceKey(topology);
        }
        CreateApiKeyRequest keyRequest = new CreateApiKeyRequest();
        keyRequest.setName(topology.getName() + "-inference");
        keyRequest.setScopes(List.of(
                "ai:chat",
                "memory:read", "memory:write",
                "delegation:write",
                "skill:read"));
        CreateApiKeyResponse created = apiKeyManagementService.createForTenant(keyRequest, topology.getTenantId());
        topology.setInferenceApiKeyId(created.getId());
        topologyRepository.save(topology);
        return created.getRawKey();
    }

    private String findExistingInferenceKey(AgentTopology topology) {
        alignTopologyInferenceKeyTenant(topology);
        List<AgentTopologyNode> nodes = topologyNodeRepository.findByTopologyId(topology.getId());
        for (AgentTopologyNode node : nodes) {
            for (AppEnv env : appEnvRepository.findByApplicationId(node.getApplicationId())) {
                if ("OPENAI_API_KEY".equals(env.getKey()) && env.isSecret()) {
                    return cryptoService.decrypt(env.getValue());
                }
            }
        }
        CreateApiKeyRequest keyRequest = new CreateApiKeyRequest();
        keyRequest.setName(topology.getName() + "-inference");
        keyRequest.setScopes(List.of(
                "ai:chat",
                "memory:read", "memory:write",
                "delegation:write",
                "skill:read"));
        CreateApiKeyResponse created = apiKeyManagementService.createForTenant(keyRequest, topology.getTenantId());
        topology.setInferenceApiKeyId(created.getId());
        topologyRepository.save(topology);
        return created.getRawKey();
    }

    private void alignTopologyInferenceKeyTenant(AgentTopology topology) {
        if (topology.getInferenceApiKeyId() == null || topology.getTenantId() == null) {
            return;
        }
        apiKeyRepository.findById(topology.getInferenceApiKeyId()).ifPresent(key -> {
            if (!topology.getTenantId().equals(key.getTenantId())) {
                key.setTenantId(topology.getTenantId());
                key.setUpdatedAt(Instant.now());
                apiKeyRepository.save(key);
            }
        });
    }

    private void injectInferenceEnv(Long appId, String inferenceRawKey) {
        String baseUrl = panelProperties.getInferenceUrl();
        upsertEnv(appId, "OPENAI_BASE_URL", baseUrl, false);
        upsertEnv(appId, "OPENAI_API_BASE", baseUrl, false);
        if (inferenceRawKey != null && !inferenceRawKey.isBlank()) {
            upsertEnv(appId, "OPENAI_API_KEY", inferenceRawKey, true);
        }
    }

    private void upsertEnv(Long appId, String key, String value, boolean secret) {
        List<AppEnv> existing = appEnvRepository.findByApplicationId(appId).stream()
                .filter(e -> key.equals(e.getKey()))
                .toList();
        if (existing.isEmpty()) {
            AppEnv env = new AppEnv();
            env.setApplicationId(appId);
            env.setKey(key);
            env.setSecret(secret);
            env.setValue(secret ? cryptoService.encrypt(value) : value);
            appEnvRepository.save(env);
            return;
        }
        for (AppEnv env : existing) {
            env.setSecret(secret);
            env.setValue(secret ? cryptoService.encrypt(value) : value);
            appEnvRepository.save(env);
        }
    }

    private void ensureDockerNetwork(String networkName) {
        if (!"docker".equals(runtimeProperties.getProvider())) {
            return;
        }
        AgentRuntimeProvider provider = runtimeProviderFactory.get("docker");
        if (provider instanceof DockerRuntimeProvider dockerProvider) {
            dockerProvider.ensureNetwork(networkName);
        }
    }

    private void connectToNetworkIfNeeded(Application app, String networkName) {
        if (!"docker".equals(resolveProvider(app)) || app.getRuntimeRef() == null) {
            return;
        }
        AgentRuntimeProvider provider = runtimeProviderFactory.get("docker");
        if (provider instanceof DockerRuntimeProvider dockerProvider) {
            dockerProvider.connectToNetwork(app.getRuntimeRef(), networkName);
        }
    }

    public Path resolveVolumeHostPath(Long appId, String volumeName) {
        if ("docker".equals(resolveProviderForApp(appId))) {
            return dockerHostDataPathResolver.toPanelVolumePath(appId, volumeName);
        }
        return Path.of(runtimeProperties.getDocker().getDataRoot(), String.valueOf(appId), volumeName);
    }

    public String resolveVolumeContainerPath(Long appId, String volumeName) {
        Application app = findApp(appId);
        for (Map<String, Object> volume : app.getVolumes()) {
            if (volumeName.equals(String.valueOf(volume.get("name")))) {
                return String.valueOf(volume.get("containerPath"));
            }
        }
        throw new BusinessException("数据卷不存在");
    }

    public RuntimeRef runtimeRef(Long appId) {
        Application app = findApp(appId);
        if (app.getRuntimeRef() == null) {
            throw new BusinessException("应用尚未部署");
        }
        return ref(app);
    }

    public String resolveProviderForApp(Long appId) {
        return resolveProvider(findApp(appId));
    }

    private String mapDeployStatus(String providerStatus) {
        if (providerStatus == null) {
            return "running";
        }
        return switch (providerStatus.toLowerCase()) {
            case "running" -> "running";
            case "stopped", "exited" -> "stopped";
            case "error", "failed" -> "error";
            default -> "running";
        };
    }

    private String mapRuntimePhase(RuntimeStatus.Phase phase) {
        return switch (phase) {
            case RUNNING -> "running";
            case STOPPED -> "stopped";
            case CREATED -> "created";
            case ERROR, MISSING -> "error";
            case UNKNOWN -> "unknown";
        };
    }

    private void applyTemplateEnvDefaults(Map<String, String> env, Map<String, String> secretEnv, AgentTemplate template) {
        if (template.getEnvSchema() == null) {
            return;
        }
        Set<String> existing = new HashSet<>();
        existing.addAll(env.keySet());
        existing.addAll(secretEnv.keySet());
        for (Map<String, Object> item : template.getEnvSchema()) {
            String key = String.valueOf(item.get("key"));
            if (existing.contains(key)) {
                continue;
            }
            Object defaultValue = item.get("default");
            if (defaultValue == null || String.valueOf(defaultValue).isBlank()) {
                continue;
            }
            if (Boolean.TRUE.equals(item.get("secret"))) {
                secretEnv.put(key, String.valueOf(defaultValue));
            } else {
                env.put(key, String.valueOf(defaultValue));
            }
        }
    }

    private void saveEnv(Long appId, List<ApplicationDto.EnvItem> envItems) {
        if (envItems == null) {
            return;
        }
        validateEnvItems(envItems);
        for (ApplicationDto.EnvItem item : envItems) {
            AppEnv env = new AppEnv();
            env.setApplicationId(appId);
            env.setKey(item.getKey());
            env.setSecret(item.isSecret());
            env.setValue(item.isSecret() ? cryptoService.encrypt(item.getValue()) : item.getValue());
            appEnvRepository.save(env);
        }
    }

    private void mergeEnvUpdate(Long appId, List<ApplicationDto.EnvItem> envItems) {
        validateEnvItems(envItems);
        List<AppEnv> existing = appEnvRepository.findByApplicationId(appId);
        Map<String, AppEnv> existingByKey = existing.stream()
                .collect(Collectors.toMap(AppEnv::getKey, e -> e, (a, b) -> a));
        Set<String> newKeys = new HashSet<>();
        for (ApplicationDto.EnvItem item : envItems) {
            if (item.getKey() == null || item.getKey().isBlank()) {
                continue;
            }
            String key = item.getKey().trim();
            newKeys.add(key);
            AppEnv env = existingByKey.get(key);
            String value = item.getValue();
            boolean secret = item.isSecret();
            if (env != null && secret && isMaskedOrBlank(value)) {
                env.setSecret(true);
                appEnvRepository.save(env);
                continue;
            }
            if (value == null) {
                value = "";
            }
            if (env == null) {
                env = new AppEnv();
                env.setApplicationId(appId);
                env.setKey(key);
                if (secret && isMaskedOrBlank(value)) {
                    throw new BusinessException("新建 secret 环境变量必须提供值: " + key);
                }
            }
            env.setSecret(secret);
            env.setValue(secret ? cryptoService.encrypt(value) : value);
            appEnvRepository.save(env);
        }
        for (AppEnv env : existing) {
            if (!newKeys.contains(env.getKey())) {
                appEnvRepository.delete(env);
            }
        }
    }

    private void validateEnvItems(List<ApplicationDto.EnvItem> envItems) {
        if (envItems == null) {
            return;
        }
        if (envItems.size() > 200) {
            throw new BusinessException("环境变量数量不能超过 200 个");
        }
        Set<String> keys = new HashSet<>();
        for (ApplicationDto.EnvItem item : envItems) {
            if (item.getKey() == null || item.getKey().isBlank()) {
                continue;
            }
            String key = item.getKey().trim();
            if (!key.matches("^[A-Za-z_][A-Za-z0-9_.-]*$")) {
                throw new BusinessException("环境变量 key 格式无效: " + key);
            }
            if (!keys.add(key)) {
                throw new BusinessException("环境变量 key 重复: " + key);
            }
            if (!item.isSecret() && item.getValue() != null && item.getValue().length() > 32768) {
                throw new BusinessException("环境变量值过长: " + key);
            }
        }
    }

    private boolean isMaskedOrBlank(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return value.contains("****");
    }

    private Application findApp(Long id) {
        Application app = applicationRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        TenantAccessHelper.requireOwnedTenant(app.getTenantId(), "应用不存在");
        return app;
    }

    private void syncKanbanForApp(Application app) {
        if (app.getTenantId() == null) {
            return;
        }
        try {
            taskKanbanService.syncBoardsForTenant(app.getTenantId());
        } catch (Exception e) {
            log.warn("同步租户 {} 看板失败: {}", app.getTenantId(), e.getMessage());
        }
    }

    private RuntimeRef ref(Application app) {
        return new RuntimeRef(resolveProvider(app), app.getRuntimeRef(), app.getRuntimeNamespace());
    }

    private String resolveProvider(Application app) {
        return app.getRuntimeProvider() != null ? app.getRuntimeProvider() : runtimeProperties.getProvider();
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    private ApplicationDto toDto(Application app) {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(app.getId());
        dto.setName(app.getName());
        dto.setTemplateId(app.getTemplateId());
        templateRepository.findById(app.getTemplateId()).ifPresent(t -> {
            dto.setTemplateName(t.getName());
            dto.setTemplateCode(t.getCode());
            dto.setEnvSchema(t.getEnvSchema());
            dto.setManagementSchema(t.getManagementSchema());
        });
        dto.setOwnerId(app.getOwnerId());
        dto.setImage(app.getImage());
        dto.setTag(app.getTag());
        dto.setStatus(app.getStatus());
        dto.setPorts(app.getPorts());
        dto.setResources(app.getResources());
        dto.setVolumes(app.getVolumes());
        dto.setReplicas(app.getReplicas());
        dto.setRuntimeProvider(app.getRuntimeProvider());
        dto.setRemark(app.getRemark());
        dto.setRuntimeRef(app.getRuntimeRef());
        dto.setRuntimeNamespace(app.getRuntimeNamespace());
        dto.setEnv(appEnvRepository.findByApplicationId(app.getId()).stream().map(e -> {
            ApplicationDto.EnvItem item = new ApplicationDto.EnvItem();
            item.setKey(e.getKey());
            item.setSecret(e.isSecret());
            item.setValue(e.isSecret() ? cryptoService.mask(cryptoService.decrypt(e.getValue())) : e.getValue());
            return item;
        }).toList());
        dto.setAccessUrls(buildAccessUrls(app));
        return dto;
    }

    private void persistResolvedPorts(Application app, List<PortMapping> resolvedPorts) {
        List<Map<String, Object>> ports = new ArrayList<>();
        for (Map<String, Object> port : app.getPorts()) {
            Map<String, Object> copy = new LinkedHashMap<>(port);
            String name = String.valueOf(port.get("name"));
            resolvedPorts.stream()
                    .filter(p -> p.name().equals(name) && p.hostPort() != null)
                    .findFirst()
                    .ifPresent(p -> copy.put("hostPort", p.hostPort()));
            ports.add(copy);
        }
        app.setPorts(ports);
    }

    private List<Map<String, Object>> buildAccessUrls(Application app) {
        String host = resolveAccessHost(app);
        List<Map<String, Object>> urls = new ArrayList<>();
        if (app.getPorts() == null) {
            return urls;
        }
        for (Map<String, Object> port : app.getPorts()) {
            if (!Boolean.TRUE.equals(port.get("expose"))) {
                continue;
            }
            Object hostPort = port.get("hostPort");
            if (hostPort == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", port.get("name"));
            entry.put("url", host + ":" + hostPort);
            entry.put("protocol", port.getOrDefault("protocol", "TCP"));
            urls.add(entry);
        }
        return urls;
    }

    private String resolveAccessHost(Application app) {
        if ("k8s".equals(resolveProvider(app))) {
            return runtimeProperties.getK8s().getAccessHost();
        }
        return runtimeProperties.getDocker().getAccessHost();
    }

    private void audit(String action, Long id, HttpServletRequest request) {
        auditService.log(requireCurrentUserId(), SecurityUtils.getCurrentUsername(),
                action, "application", String.valueOf(id), null, request.getRemoteAddr(), "success");
    }
}
