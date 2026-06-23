package com.agentpanel.application.service;

import com.agentpanel.application.dto.*;
import com.agentpanel.application.entity.AgentTopology;
import com.agentpanel.application.entity.AgentTopologyNode;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentLinkRepository;
import com.agentpanel.application.repository.AgentTopologyNodeRepository;
import com.agentpanel.application.repository.AgentTopologyRepository;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.auth.repository.ApiKeyRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.TenantAccessHelper;
import com.agentpanel.system.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TopologyService {

    private final AgentTopologyRepository topologyRepository;
    private final AgentTopologyNodeRepository nodeRepository;
    private final AgentLinkRepository linkRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final TopologyLinkService topologyLinkService;
    private final ApiKeyRepository apiKeyRepository;
    private final AuditService auditService;

    public List<TopologyDto> list() {
        List<AgentTopology> topologies = SecurityUtils.isSuperAdmin()
                ? topologyRepository.findByDeletedFalseOrderByUpdatedAtDesc()
                : topologyRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(
                        SecurityUtils.getCurrentTenantId());
        return topologies.stream()
                .map(this::toDto)
                .toList();
    }

    public TopologyDto get(Long id) {
        return toDto(findTopology(id));
    }

    @Transactional
    public TopologyDto create(CreateTopologyRequest request, HttpServletRequest httpRequest) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("拓扑名称不能为空");
        }
        Long tenantId = SecurityUtils.getCurrentTenantId();
        if (topologyRepository.existsByNameAndTenantIdAndDeletedFalse(request.getName().trim(), tenantId)) {
            throw new BusinessException("拓扑名称已存在");
        }
        AgentTopology topology = new AgentTopology();
        topology.setName(request.getName().trim());
        topology.setDescription(request.getDescription());
        topology.setNetworkName(request.getNetworkName() != null && !request.getNetworkName().isBlank()
                ? request.getNetworkName().trim() : "agentpanel-net");
        topology.setStatus("draft");
        topology.setOwnerId(requireCurrentUserId());
        topology.setTenantId(SecurityUtils.getCurrentTenantId());
        AgentTopology saved = topologyRepository.save(topology);
        audit("create", saved.getId(), httpRequest);
        return toDto(saved);
    }

    @Transactional
    public TopologyDto update(Long id, CreateTopologyRequest request, HttpServletRequest httpRequest) {
        AgentTopology topology = findTopology(id);
        if (request.getName() != null && !request.getName().isBlank()
                && !request.getName().trim().equals(topology.getName())
                && topologyRepository.existsByNameAndTenantIdAndDeletedFalse(
                        request.getName().trim(), topology.getTenantId())) {
            throw new BusinessException("拓扑名称已存在");
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            topology.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            topology.setDescription(request.getDescription());
        }
        if (request.getNetworkName() != null && !request.getNetworkName().isBlank()) {
            topology.setNetworkName(request.getNetworkName().trim());
        }
        AgentTopology saved = topologyRepository.save(topology);
        audit("update", saved.getId(), httpRequest);
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        AgentTopology topology = findTopology(id);
        topology.setDeleted(true);
        topologyRepository.save(topology);
        audit("delete", id, httpRequest);
    }

    @Transactional
    public TopologyDto addNode(Long topologyId, AddTopologyNodeRequest request, HttpServletRequest httpRequest) {
        AgentTopology topology = findTopology(topologyId);
        if (request.getApplicationId() == null) {
            throw new BusinessException("请选择应用");
        }
        Application app = applicationRepository.findByIdAndDeletedFalse(request.getApplicationId())
                .orElseThrow(() -> new BusinessException("应用不存在"));
        if (app.getTenantId() == null || !app.getTenantId().equals(topology.getTenantId())) {
            throw new BusinessException("应用不存在");
        }
        if (nodeRepository.findByTopologyIdAndApplicationId(topologyId, app.getId()).isPresent()) {
            throw new BusinessException("应用已在拓扑中");
        }
        String role = normalizeRole(request.getRole());
        AgentTopologyNode node = new AgentTopologyNode();
        node.setTopologyId(topologyId);
        node.setApplicationId(app.getId());
        node.setRole(role);
        node.setConfig(request.getConfig() != null ? request.getConfig() : Map.of());
        nodeRepository.save(node);
        audit("add-node", topologyId, httpRequest);
        return toDto(topology);
    }

    @Transactional
    public TopologyDto removeNode(Long topologyId, Long applicationId, HttpServletRequest httpRequest) {
        findTopology(topologyId);
        nodeRepository.findByTopologyIdAndApplicationId(topologyId, applicationId).ifPresent(node ->
                linkRepository.findByTopologyId(topologyId).stream()
                        .filter(link -> link.getFromNodeId().equals(node.getId()) || link.getToNodeId().equals(node.getId()))
                        .forEach(linkRepository::delete));
        nodeRepository.deleteByTopologyIdAndApplicationId(topologyId, applicationId);
        audit("remove-node", topologyId, httpRequest);
        return toDto(findTopology(topologyId));
    }

    @Transactional
    public TopologyDto deploy(Long topologyId, HttpServletRequest httpRequest) {
        return applicationService.deployTopology(topologyId, httpRequest);
    }

    @Transactional
    public TopologyDto redeploy(Long topologyId, HttpServletRequest httpRequest) {
        return applicationService.redeployTopology(topologyId, httpRequest);
    }

    public List<TopologyLinkDto> listLinks(Long topologyId) {
        return topologyLinkService.listLinks(topologyId);
    }

    @Transactional
    public TopologyLinkDto addLink(Long topologyId, AddTopologyLinkRequest request, HttpServletRequest httpRequest) {
        TopologyLinkDto link = topologyLinkService.addLink(topologyId, request, httpRequest);
        return link;
    }

    @Transactional
    public void removeLink(Long topologyId, Long linkId, HttpServletRequest httpRequest) {
        topologyLinkService.removeLink(topologyId, linkId, httpRequest);
    }

    private AgentTopology findTopology(Long id) {
        AgentTopology topology = topologyRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("拓扑不存在"));
        TenantAccessHelper.requireOwnedTenant(topology.getTenantId(), "拓扑不存在");
        return topology;
    }

    private TopologyDto toDto(AgentTopology topology) {
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
        dto.setNodes(nodeRepository.findByTopologyId(topology.getId()).stream().map(node -> {
            TopologyDto.TopologyNodeDto nodeDto = new TopologyDto.TopologyNodeDto();
            nodeDto.setId(node.getId());
            nodeDto.setApplicationId(node.getApplicationId());
            nodeDto.setRole(node.getRole());
            nodeDto.setConfig(node.getConfig());
            applicationRepository.findByIdAndDeletedFalse(node.getApplicationId()).ifPresent(app -> {
                nodeDto.setApplicationName(app.getName());
                nodeDto.setApplicationStatus(app.getStatus());
            });
            return nodeDto;
        }).toList());
        Map<Long, AgentTopologyNode> nodeMap = nodeRepository.findByTopologyId(topology.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(AgentTopologyNode::getId, n -> n));
        dto.setLinks(linkRepository.findByTopologyId(topology.getId()).stream()
                .map(link -> topologyLinkService.toDto(link, nodeMap))
                .toList());
        if (topology.getInferenceApiKeyId() != null) {
            apiKeyRepository.findById(topology.getInferenceApiKeyId()).ifPresent(key ->
                    dto.setNeedsKeyRedeploy(key.isDeprecated() && "deployed".equals(topology.getStatus())));
        }
        if ("deployed".equals(topology.getStatus())) {
            List<AgentTopologyNode> nodes = nodeRepository.findByTopologyId(topology.getId());
            dto.setMemberAccessUrls(applicationService.buildMemberAccessUrlsForTopology(nodes));
            List<InjectedEnvDto> envPreview = new ArrayList<>(applicationService.previewInjectedEnv(topology.getId(), nodes));
            envPreview.addAll(applicationService.previewSkillEnv(topology.getId(), nodes));
            envPreview.addAll(applicationService.previewAgentPanelIntegrationEnv(nodes));
            dto.setInjectedEnv(envPreview);
            dto.setInjectedSkills(applicationService.buildInjectedSkillsSummary(topology.getId()));
        }
        return dto;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "worker";
        }
        String normalized = role.trim().toLowerCase();
        if (!normalized.equals("gateway") && !normalized.equals("worker")) {
            throw new BusinessException("角色必须为 gateway 或 worker");
        }
        return normalized;
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    private void audit(String action, Long id, HttpServletRequest request) {
        auditService.log(requireCurrentUserId(), SecurityUtils.getCurrentUsername(),
                action, "topology", String.valueOf(id), null, request.getRemoteAddr(), "success");
    }
}
