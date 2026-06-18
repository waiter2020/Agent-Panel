package com.agentpanel.application;

import com.agentpanel.application.dto.*;
import com.agentpanel.application.service.TopologyService;
import com.agentpanel.common.ApiResponse;
import com.agentpanel.memory.dto.SkillReloadNotifyResult;
import com.agentpanel.memory.service.SharedSkillService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topologies")
@RequiredArgsConstructor
public class TopologyController {

    private final TopologyService topologyService;
    private final SharedSkillService sharedSkillService;

    @GetMapping
    @PreAuthorize("hasAuthority('topology:read')")
    public ApiResponse<List<TopologyDto>> list() {
        return ApiResponse.ok(topologyService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('topology:read')")
    public ApiResponse<TopologyDto> get(@PathVariable Long id) {
        return ApiResponse.ok(topologyService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('topology:write')")
    public ApiResponse<TopologyDto> create(@RequestBody CreateTopologyRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(topologyService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('topology:write')")
    public ApiResponse<TopologyDto> update(@PathVariable Long id, @RequestBody CreateTopologyRequest request,
                                           HttpServletRequest httpRequest) {
        return ApiResponse.ok(topologyService.update(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('topology:write')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        topologyService.delete(id, httpRequest);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/nodes")
    @PreAuthorize("hasAuthority('topology:write')")
    public ApiResponse<TopologyDto> addNode(@PathVariable Long id, @RequestBody AddTopologyNodeRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(topologyService.addNode(id, request, httpRequest));
    }

    @DeleteMapping("/{id}/nodes/{applicationId}")
    @PreAuthorize("hasAuthority('topology:write')")
    public ApiResponse<TopologyDto> removeNode(@PathVariable Long id, @PathVariable Long applicationId,
                                               HttpServletRequest httpRequest) {
        return ApiResponse.ok(topologyService.removeNode(id, applicationId, httpRequest));
    }

    @PostMapping("/{id}/deploy")
    @PreAuthorize("hasAuthority('topology:deploy')")
    public ApiResponse<TopologyDto> deploy(@PathVariable Long id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(topologyService.deploy(id, httpRequest));
    }

    @PostMapping("/{id}/redeploy")
    @PreAuthorize("hasAuthority('topology:deploy')")
    public ApiResponse<TopologyDto> redeploy(@PathVariable Long id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(topologyService.redeploy(id, httpRequest));
    }

    @GetMapping("/{id}/links")
    @PreAuthorize("hasAuthority('topology:read')")
    public ApiResponse<List<TopologyLinkDto>> listLinks(@PathVariable Long id) {
        return ApiResponse.ok(topologyService.listLinks(id));
    }

    @PostMapping("/{id}/links")
    @PreAuthorize("hasAuthority('topology:write')")
    public ApiResponse<TopologyLinkDto> addLink(@PathVariable Long id, @RequestBody AddTopologyLinkRequest request,
                                                HttpServletRequest httpRequest) {
        return ApiResponse.ok(topologyService.addLink(id, request, httpRequest));
    }

    @DeleteMapping("/{id}/links/{linkId}")
    @PreAuthorize("hasAuthority('topology:write')")
    public ApiResponse<Void> removeLink(@PathVariable Long id, @PathVariable Long linkId,
                                        HttpServletRequest httpRequest) {
        topologyService.removeLink(id, linkId, httpRequest);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/notify-skills-reload")
    @PreAuthorize("hasAuthority('skill:write')")
    public ApiResponse<SkillReloadNotifyResult> notifySkillsReload(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(sharedSkillService.notifyReloadForTopology(id, httpRequest));
    }
}
