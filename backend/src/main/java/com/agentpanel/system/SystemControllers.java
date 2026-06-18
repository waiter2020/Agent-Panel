package com.agentpanel.system;

import com.agentpanel.common.ApiResponse;
import com.agentpanel.common.PageResult;
import com.agentpanel.system.dto.RoleDto;
import com.agentpanel.system.entity.AuditLog;
import com.agentpanel.system.repository.AuditLogRepository;
import com.agentpanel.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SystemControllers {

    @RestController
    @RequestMapping("/api/roles")
    @RequiredArgsConstructor
    static class RoleController {
        private final RoleService roleService;

        @GetMapping
        @PreAuthorize("hasAuthority('system:role:read')")
        public ApiResponse<List<RoleDto>> list() {
            return ApiResponse.ok(roleService.list());
        }

        @PostMapping
        @PreAuthorize("hasAuthority('system:role:write')")
        public ApiResponse<RoleDto> create(@RequestBody RoleDto dto) {
            return ApiResponse.ok(roleService.create(dto));
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('system:role:write')")
        public ApiResponse<RoleDto> update(@PathVariable Long id, @RequestBody RoleDto dto) {
            return ApiResponse.ok(roleService.update(id, dto));
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('system:role:write')")
        public ApiResponse<Void> delete(@PathVariable Long id) {
            roleService.delete(id);
            return ApiResponse.ok();
        }
    }

    @RestController
    @RequestMapping("/api/menus")
    @RequiredArgsConstructor
    static class MenuController {
        private final com.agentpanel.system.service.MenuService menuService;

        @GetMapping
        @PreAuthorize("hasAuthority('system:menu:read')")
        public ApiResponse<List<com.agentpanel.system.dto.MenuDto>> list() {
            return ApiResponse.ok(menuService.list());
        }

        @PostMapping
        @PreAuthorize("hasAuthority('system:menu:write')")
        public ApiResponse<com.agentpanel.system.dto.MenuDto> create(@RequestBody com.agentpanel.system.dto.MenuDto dto) {
            return ApiResponse.ok(menuService.create(dto));
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('system:menu:write')")
        public ApiResponse<com.agentpanel.system.dto.MenuDto> update(@PathVariable Long id, @RequestBody com.agentpanel.system.dto.MenuDto dto) {
            return ApiResponse.ok(menuService.update(id, dto));
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('system:menu:write')")
        public ApiResponse<Void> delete(@PathVariable Long id) {
            menuService.delete(id);
            return ApiResponse.ok();
        }
    }

    @RestController
    @RequestMapping("/api/permissions")
    @RequiredArgsConstructor
    static class PermissionController {
        private final com.agentpanel.system.service.PermissionService permissionService;

        @GetMapping
        @PreAuthorize("hasAuthority('system:role:read')")
        public ApiResponse<List<com.agentpanel.system.dto.PermissionDto>> list() {
            return ApiResponse.ok(permissionService.list());
        }

        @PostMapping
        @PreAuthorize("hasAuthority('system:role:write')")
        public ApiResponse<com.agentpanel.system.dto.PermissionDto> create(@RequestBody com.agentpanel.system.dto.PermissionDto dto) {
            return ApiResponse.ok(permissionService.create(dto));
        }
    }

    @RestController
    @RequestMapping("/api/audit-logs")
    @RequiredArgsConstructor
    static class AuditLogController {
        private final AuditLogRepository auditLogRepository;

        @GetMapping
        @PreAuthorize("hasAuthority('audit:read')")
        public ApiResponse<PageResult<AuditLog>> list(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
            var result = auditLogRepository.findAllByOrderByAtDesc(PageRequest.of(page - 1, pageSize));
            return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
        }
    }

    @RestController
    @RequestMapping("/api/settings")
    @RequiredArgsConstructor
    static class SettingController {
        private final com.agentpanel.system.service.SettingService settingService;

        @GetMapping
        @PreAuthorize("hasAuthority('system:setting:read')")
        public ApiResponse<java.util.Map<String, String>> getSettings() {
            return ApiResponse.ok(settingService.getAll());
        }

        @PutMapping
        @PreAuthorize("hasAuthority('system:setting:write')")
        public ApiResponse<java.util.Map<String, String>> updateSettings(@RequestBody java.util.Map<String, String> settings) {
            return ApiResponse.ok(settingService.update(settings));
        }
    }
}
