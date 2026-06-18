package com.agentpanel.system;

import com.agentpanel.common.ApiResponse;
import com.agentpanel.system.dto.TenantDto;
import com.agentpanel.system.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<TenantDto>> list() {
        return ApiResponse.ok(tenantService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<TenantDto> get(@PathVariable Long id) {
        return ApiResponse.ok(tenantService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<TenantDto> create(@RequestBody TenantDto dto) {
        return ApiResponse.ok(tenantService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<TenantDto> update(@PathVariable Long id, @RequestBody TenantDto dto) {
        return ApiResponse.ok(tenantService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return ApiResponse.ok();
    }
}
