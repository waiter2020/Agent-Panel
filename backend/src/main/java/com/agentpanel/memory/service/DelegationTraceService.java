package com.agentpanel.memory.service;

import com.agentpanel.application.entity.AgentTopology;
import com.agentpanel.application.entity.Application;
import com.agentpanel.application.repository.AgentTopologyRepository;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.TenantAccessHelper;
import com.agentpanel.memory.dto.DelegationTraceDto;
import com.agentpanel.memory.dto.RecordDelegationRequest;
import com.agentpanel.memory.dto.UpdateDelegationRequest;
import com.agentpanel.memory.entity.DelegationTrace;
import com.agentpanel.memory.repository.DelegationTraceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DelegationTraceService {

    private static final Set<String> STATUSES = Set.of("running", "completed", "failed", "cancelled");

    private final DelegationTraceRepository delegationTraceRepository;
    private final AgentTopologyRepository topologyRepository;
    private final ApplicationRepository applicationRepository;

    @Transactional
    public DelegationTraceDto record(RecordDelegationRequest request) {
        if (request.getTopologyId() == null) {
            throw new BusinessException("topologyId 不能为空");
        }
        if (request.getParentAppId() == null || request.getChildAppId() == null) {
            throw new BusinessException("parentAppId 与 childAppId 不能为空");
        }
        if (request.getTaskSummary() == null || request.getTaskSummary().isBlank()) {
            throw new BusinessException("任务摘要不能为空");
        }
        AgentTopology topology = requireTopology(request.getTopologyId());
        requireApplicationInTenant(request.getParentAppId(), topology.getTenantId(), "父应用不存在");
        requireApplicationInTenant(request.getChildAppId(), topology.getTenantId(), "子应用不存在");

        DelegationTrace trace = new DelegationTrace();
        trace.setTopologyId(request.getTopologyId());
        trace.setParentAppId(request.getParentAppId());
        trace.setChildAppId(request.getChildAppId());
        trace.setTaskSummary(request.getTaskSummary().trim());
        trace.setStatus(normalizeStatus(request.getStatus()));
        trace.setStartedAt(request.getStartedAt() != null ? request.getStartedAt() : Instant.now());
        trace.setCompletedAt(request.getCompletedAt());
        trace.setResult(request.getResult() == null ? Map.of() : request.getResult());
        if (trace.getCompletedAt() == null && isTerminal(trace.getStatus())) {
            trace.setCompletedAt(Instant.now());
        }
        return toDto(delegationTraceRepository.save(trace));
    }

    public List<DelegationTraceDto> list(Long topologyId, Long applicationId) {
        if (topologyId == null && applicationId == null) {
            throw new BusinessException("请指定 topologyId 或 applicationId");
        }
        if (topologyId != null) {
            AgentTopology topology = requireTopology(topologyId);
            List<DelegationTrace> traces = delegationTraceRepository.findByTopologyIdOrderByStartedAtDesc(topologyId);
            if (applicationId != null) {
                requireApplicationInTenant(applicationId, topology.getTenantId(), "应用不存在");
                traces = traces.stream()
                        .filter(trace -> applicationId.equals(trace.getParentAppId())
                                || applicationId.equals(trace.getChildAppId()))
                        .toList();
            }
            return traces.stream().map(this::toDto).toList();
        }
        requireApplication(applicationId, "应用不存在");
        return delegationTraceRepository.findByParentAppIdOrChildAppIdOrderByStartedAtDesc(applicationId, applicationId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public DelegationTraceDto update(Long id, UpdateDelegationRequest request) {
        DelegationTrace trace = delegationTraceRepository.findById(id)
                .orElseThrow(() -> new BusinessException("委派记录不存在"));
        requireTopology(trace.getTopologyId());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            trace.setStatus(normalizeStatus(request.getStatus()));
        }
        if (request.getResult() != null) {
            trace.setResult(request.getResult());
        }
        if (request.getCompletedAt() != null) {
            trace.setCompletedAt(request.getCompletedAt());
        } else if (request.getStatus() != null && isTerminal(trace.getStatus())) {
            trace.setCompletedAt(Instant.now());
        }
        return toDto(delegationTraceRepository.save(trace));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "running";
        }
        String normalized = status.trim().toLowerCase();
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException("status 必须为 running、completed、failed 或 cancelled");
        }
        return normalized;
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status) || "cancelled".equals(status);
    }

    private AgentTopology requireTopology(Long topologyId) {
        AgentTopology topology = topologyRepository.findByIdAndDeletedFalse(topologyId)
                .orElseThrow(() -> new BusinessException("拓扑不存在"));
        TenantAccessHelper.requireOwnedTenant(topology.getTenantId(), "拓扑不存在");
        return topology;
    }

    private Application requireApplication(Long applicationId, String message) {
        Application app = applicationRepository.findByIdAndDeletedFalse(applicationId)
                .orElseThrow(() -> new BusinessException(message));
        TenantAccessHelper.requireOwnedTenant(app.getTenantId(), message);
        return app;
    }

    private void requireApplicationInTenant(Long applicationId, Long tenantId, String message) {
        Application app = requireApplication(applicationId, message);
        if (tenantId == null || !tenantId.equals(app.getTenantId())) {
            throw new BusinessException(message);
        }
    }

    private DelegationTraceDto toDto(DelegationTrace trace) {
        DelegationTraceDto dto = new DelegationTraceDto();
        dto.setId(trace.getId());
        dto.setTopologyId(trace.getTopologyId());
        dto.setParentAppId(trace.getParentAppId());
        dto.setChildAppId(trace.getChildAppId());
        applicationRepository.findByIdAndDeletedFalse(trace.getParentAppId())
                .ifPresent(a -> dto.setParentAppName(a.getName()));
        applicationRepository.findByIdAndDeletedFalse(trace.getChildAppId())
                .ifPresent(a -> dto.setChildAppName(a.getName()));
        dto.setTaskSummary(trace.getTaskSummary());
        dto.setStatus(trace.getStatus());
        dto.setStartedAt(trace.getStartedAt());
        dto.setCompletedAt(trace.getCompletedAt());
        dto.setResult(trace.getResult());
        return dto;
    }
}
