package com.agentpanel.application;

import com.agentpanel.application.dto.CreateMcpEndpointRequest;
import com.agentpanel.application.dto.McpEndpointDto;
import com.agentpanel.application.dto.UpdateMcpEndpointRequest;
import com.agentpanel.application.service.McpEndpointService;
import com.agentpanel.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp-endpoints")
@RequiredArgsConstructor
public class McpEndpointController {

    private final McpEndpointService mcpEndpointService;

    @GetMapping
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<List<McpEndpointDto>> list(@RequestParam(required = false) Long applicationId) {
        if (applicationId != null) {
            return ApiResponse.ok(mcpEndpointService.listByApplication(applicationId));
        }
        return ApiResponse.ok(mcpEndpointService.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<McpEndpointDto> get(@PathVariable Long id) {
        return ApiResponse.ok(mcpEndpointService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<McpEndpointDto> create(@RequestBody CreateMcpEndpointRequest request) {
        return ApiResponse.ok(mcpEndpointService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<McpEndpointDto> update(@PathVariable Long id, @RequestBody UpdateMcpEndpointRequest request) {
        return ApiResponse.ok(mcpEndpointService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mcpEndpointService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/discover")
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<McpEndpointDto> discover(@PathVariable Long id) {
        return ApiResponse.ok(mcpEndpointService.discover(id));
    }
}
