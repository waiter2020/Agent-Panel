package com.agentpanel.storage;

import com.agentpanel.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;
    private final AppStorageScope appStorageScope;

    @GetMapping
    @PreAuthorize("hasAuthority('file:read')")
    public ApiResponse<List<StorageService.ObjectInfo>> list(
            @RequestParam(required = false) String prefix,
            @RequestParam(required = false) Long appId) {
        if (appId != null) {
            return ApiResponse.ok(storageService.list(appStorageScope.resolveListPrefix(appId, prefix)));
        }
        return ApiResponse.ok(storageService.list(appStorageScope.resolveGlobalListPrefix(prefix)));
    }

    @PostMapping("/presign")
    @PreAuthorize("hasAuthority('file:write')")
    public ApiResponse<Map<String, String>> presign(@RequestBody Map<String, Object> body) {
        String key = body.get("key") == null ? null : String.valueOf(body.get("key"));
        String type = body.get("type") == null ? "put" : String.valueOf(body.get("type"));
        Long appId = parseAppId(body.get("appId"));
        if (appId != null) {
            key = appStorageScope.resolveKey(appId, key);
        } else {
            key = appStorageScope.resolveGlobalKey(key);
        }
        var url = "get".equalsIgnoreCase(type)
                ? storageService.presignedGetUrl(key, Duration.ofMinutes(15))
                : storageService.presignedPutUrl(key, Duration.ofMinutes(15));
        return ApiResponse.ok(Map.of("url", url.toString(), "key", key));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('file:write')")
    public ApiResponse<Void> delete(
            @RequestParam String key,
            @RequestParam(required = false) Long appId) {
        storageService.delete(appStorageScope.resolveDeleteKey(appId, key));
        return ApiResponse.ok();
    }

    private Long parseAppId(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new com.agentpanel.common.BusinessException("appId 无效");
        }
    }
}
