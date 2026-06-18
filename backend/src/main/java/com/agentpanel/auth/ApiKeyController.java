package com.agentpanel.auth;

import com.agentpanel.auth.dto.ApiKeyDto;
import com.agentpanel.auth.dto.CreateApiKeyRequest;
import com.agentpanel.auth.dto.CreateApiKeyResponse;
import com.agentpanel.auth.dto.UpdateApiKeyRequest;
import com.agentpanel.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyManagementService apiKeyManagementService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:apikey:read')")
    public ApiResponse<List<ApiKeyDto>> list() {
        return ApiResponse.ok(apiKeyManagementService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:apikey:write')")
    public ApiResponse<CreateApiKeyResponse> create(@RequestBody CreateApiKeyRequest request) {
        return ApiResponse.ok(apiKeyManagementService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:apikey:write')")
    public ApiResponse<ApiKeyDto> update(@PathVariable Long id, @RequestBody UpdateApiKeyRequest request) {
        return ApiResponse.ok(apiKeyManagementService.update(id, request));
    }

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasAuthority('system:apikey:write')")
    public ApiResponse<CreateApiKeyResponse> rotate(@PathVariable Long id) {
        return ApiResponse.ok(apiKeyManagementService.rotate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:apikey:write')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        apiKeyManagementService.delete(id);
        return ApiResponse.ok();
    }
}
