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

    @GetMapping
    @PreAuthorize("hasAuthority('file:read')")
    public ApiResponse<List<StorageService.ObjectInfo>> list(@RequestParam(required = false) String prefix) {
        return ApiResponse.ok(storageService.list(prefix));
    }

    @PostMapping("/presign")
    @PreAuthorize("hasAuthority('file:write')")
    public ApiResponse<Map<String, String>> presign(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        String type = body.getOrDefault("type", "put");
        var url = "get".equalsIgnoreCase(type)
                ? storageService.presignedGetUrl(key, Duration.ofMinutes(15))
                : storageService.presignedPutUrl(key, Duration.ofMinutes(15));
        return ApiResponse.ok(Map.of("url", url.toString()));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('file:write')")
    public ApiResponse<Void> delete(@RequestParam String key) {
        storageService.delete(key);
        return ApiResponse.ok();
    }
}
