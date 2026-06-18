package com.agentpanel.memory;

import com.agentpanel.common.ApiResponse;
import com.agentpanel.memory.dto.SharedMemoryDto;
import com.agentpanel.memory.dto.StoreMemoryRequest;
import com.agentpanel.memory.service.SharedMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final SharedMemoryService sharedMemoryService;

    @PostMapping
    @PreAuthorize("hasAuthority('memory:write')")
    public ApiResponse<SharedMemoryDto> store(@RequestBody StoreMemoryRequest request) {
        return ApiResponse.ok(sharedMemoryService.store(request));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('memory:read')")
    public ApiResponse<List<SharedMemoryDto>> search(
            @RequestParam("q") String query,
            @RequestParam(required = false) Long topologyId,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(sharedMemoryService.search(query, topologyId, applicationId, limit));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('memory:read')")
    public ApiResponse<List<SharedMemoryDto>> list(
            @RequestParam(required = false) Long topologyId,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) String scope) {
        return ApiResponse.ok(sharedMemoryService.list(topologyId, applicationId, scope));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('memory:write')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sharedMemoryService.delete(id);
        return ApiResponse.ok();
    }
}
