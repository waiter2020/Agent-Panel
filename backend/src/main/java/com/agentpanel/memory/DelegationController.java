package com.agentpanel.memory;

import com.agentpanel.common.ApiResponse;
import com.agentpanel.memory.dto.DelegationTraceDto;
import com.agentpanel.memory.dto.RecordDelegationRequest;
import com.agentpanel.memory.dto.UpdateDelegationRequest;
import com.agentpanel.memory.service.DelegationTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delegations")
@RequiredArgsConstructor
public class DelegationController {

    private final DelegationTraceService delegationTraceService;

    @PostMapping
    @PreAuthorize("hasAuthority('delegation:write')")
    public ApiResponse<DelegationTraceDto> record(@RequestBody RecordDelegationRequest request) {
        return ApiResponse.ok(delegationTraceService.record(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('delegation:read')")
    public ApiResponse<List<DelegationTraceDto>> list(
            @RequestParam(required = false) Long topologyId,
            @RequestParam(required = false) Long applicationId) {
        return ApiResponse.ok(delegationTraceService.list(topologyId, applicationId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('delegation:write')")
    public ApiResponse<DelegationTraceDto> update(
            @PathVariable Long id,
            @RequestBody UpdateDelegationRequest request) {
        return ApiResponse.ok(delegationTraceService.update(id, request));
    }

    /**
     * Webhook for external agents to report delegation status. Authenticate with API key
     * scope {@code delegation:write} via {@code X-API-Key} or {@code Authorization: Bearer}.
     */
    @PostMapping("/webhook")
    @PreAuthorize("hasAuthority('delegation:write')")
    public ApiResponse<DelegationTraceDto> webhook(
            @RequestParam Long id,
            @RequestBody UpdateDelegationRequest request) {
        return ApiResponse.ok(delegationTraceService.update(id, request));
    }
}
