package com.agentpanel.application.service;

import com.agentpanel.application.dto.AddTopologyLinkRequest;
import com.agentpanel.application.dto.TopologyLinkDto;
import com.agentpanel.application.entity.AgentLink;
import com.agentpanel.application.entity.AgentTopology;
import com.agentpanel.application.entity.AgentTopologyNode;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentLinkRepository;
import com.agentpanel.application.repository.AgentTopologyNodeRepository;
import com.agentpanel.application.repository.AgentTopologyRepository;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.TenantAccessHelper;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.system.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TopologyLinkService {

    private final AgentLinkRepository linkRepository;
    private final AgentTopologyRepository topologyRepository;
    private final AgentTopologyNodeRepository nodeRepository;
    private final ApplicationRepository applicationRepository;
    private final PeerUrlResolver peerUrlResolver;
    private final AuditService auditService;

    public List<TopologyLinkDto> listLinks(Long topologyId) {
        findTopology(topologyId);
        return linkRepository.findByTopologyId(topologyId).stream()
                .map(link -> toDto(link, buildNodeMap(topologyId)))
                .toList();
    }

    @Transactional
    public TopologyLinkDto addLink(Long topologyId, AddTopologyLinkRequest request, HttpServletRequest httpRequest) {
        findTopology(topologyId);
        if (request.getFromNodeId() == null || request.getToNodeId() == null) {
            throw new BusinessException("请选择源节点与目标节点");
        }
        if (request.getFromNodeId().equals(request.getToNodeId())) {
            throw new BusinessException("不能连接同一节点");
        }
        AgentTopologyNode fromNode = findNode(topologyId, request.getFromNodeId());
        AgentTopologyNode toNode = findNode(topologyId, request.getToNodeId());
        String protocol = normalizeProtocol(request.getProtocol());
        if (linkRepository.findByTopologyId(topologyId).stream()
                .anyMatch(l -> l.getFromNodeId().equals(fromNode.getId())
                        && l.getToNodeId().equals(toNode.getId())
                        && l.getProtocol().equals(protocol))) {
            throw new BusinessException("相同方向的链路已存在");
        }
        AgentLink link = new AgentLink();
        link.setTopologyId(topologyId);
        link.setFromNodeId(fromNode.getId());
        link.setToNodeId(toNode.getId());
        link.setProtocol(protocol);
        link.setConfig(request.getConfig() != null ? request.getConfig() : Map.of());
        AgentLink saved = linkRepository.save(link);
        audit("add-link", topologyId, httpRequest);
        return toDto(saved, buildNodeMap(topologyId));
    }

    @Transactional
    public void removeLink(Long topologyId, Long linkId, HttpServletRequest httpRequest) {
        findTopology(topologyId);
        AgentLink link = linkRepository.findByIdAndTopologyId(linkId, topologyId)
                .orElseThrow(() -> new BusinessException("链路不存在"));
        linkRepository.delete(link);
        audit("remove-link", topologyId, httpRequest);
    }

    TopologyLinkDto toDto(AgentLink link, Map<Long, AgentTopologyNode> nodeMap) {
        TopologyLinkDto dto = new TopologyLinkDto();
        dto.setId(link.getId());
        dto.setTopologyId(link.getTopologyId());
        dto.setFromNodeId(link.getFromNodeId());
        dto.setToNodeId(link.getToNodeId());
        dto.setProtocol(link.getProtocol());
        dto.setConfig(link.getConfig());
        dto.setCreatedAt(link.getCreatedAt());
        AgentTopologyNode toNode = nodeMap.get(link.getToNodeId());
        if (toNode != null) {
            applicationRepository.findByIdAndDeletedFalse(toNode.getApplicationId()).ifPresent(app -> {
                dto.setToApplicationName(app.getName());
                if ("http".equals(link.getProtocol())) {
                    dto.setPeerUrl(peerUrlResolver.resolveHttpPeerUrl(app));
                }
            });
        }
        AgentTopologyNode fromNode = nodeMap.get(link.getFromNodeId());
        if (fromNode != null) {
            applicationRepository.findByIdAndDeletedFalse(fromNode.getApplicationId())
                    .ifPresent(app -> dto.setFromApplicationName(app.getName()));
        }
        return dto;
    }

    private Map<Long, AgentTopologyNode> buildNodeMap(Long topologyId) {
        return nodeRepository.findByTopologyId(topologyId).stream()
                .collect(java.util.stream.Collectors.toMap(AgentTopologyNode::getId, n -> n));
    }

    private AgentTopology findTopology(Long id) {
        AgentTopology topology = topologyRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("拓扑不存在"));
        TenantAccessHelper.requireOwnedTenant(topology.getTenantId(), "拓扑不存在");
        return topology;
    }

    private AgentTopologyNode findNode(Long topologyId, Long nodeId) {
        return nodeRepository.findByTopologyId(topologyId).stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("拓扑节点不存在"));
    }

    private String normalizeProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return "http";
        }
        String normalized = protocol.trim().toLowerCase();
        if (!normalized.equals("http") && !normalized.equals("mcp")) {
            throw new BusinessException("协议必须为 http 或 mcp");
        }
        return normalized;
    }

    private void audit(String action, Long id, HttpServletRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        auditService.log(userId, SecurityUtils.getCurrentUsername(),
                action, "topology", String.valueOf(id), null, request.getRemoteAddr(), "success");
    }
}
