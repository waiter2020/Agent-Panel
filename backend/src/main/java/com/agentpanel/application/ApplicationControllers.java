package com.agentpanel.application;

import com.agentpanel.application.dto.ApplicationDto;
import com.agentpanel.application.entity.AgentTemplate;
import com.agentpanel.application.service.AppFileService;
import com.agentpanel.application.service.ApplicationService;
import com.agentpanel.common.ApiResponse;
import com.agentpanel.runtime.api.LogOptions;
import com.agentpanel.runtime.api.ResourceStats;
import com.agentpanel.runtime.api.RuntimeStatus;
import com.agentpanel.monitor.MonitorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplicationControllers {

    private final ApplicationService applicationService;
    private final MonitorService monitorService;
    private final AppFileService appFileService;

    @GetMapping("/api/templates")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<List<AgentTemplate>> templates() {
        return ApiResponse.ok(applicationService.listTemplates());
    }

    @GetMapping("/api/apps")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<List<ApplicationDto>> listApps() {
        return ApiResponse.ok(applicationService.listApps());
    }

    @GetMapping("/api/apps/{id}")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<ApplicationDto> getApp(@PathVariable Long id) {
        return ApiResponse.ok(applicationService.get(id));
    }

    @PostMapping("/api/apps")
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<ApplicationDto> create(@RequestBody ApplicationDto dto, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.create(dto, request));
    }

    @PutMapping("/api/apps/{id}")
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<ApplicationDto> update(@PathVariable Long id, @RequestBody ApplicationDto dto, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.update(id, dto, request));
    }

    @DeleteMapping("/api/apps/{id}")
    @PreAuthorize("hasAuthority('app:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        applicationService.delete(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/api/apps/{id}/deploy")
    @PreAuthorize("hasAuthority('app:deploy')")
    public ApiResponse<ApplicationDto> deploy(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.deploy(id, request));
    }

    @PostMapping("/api/apps/{id}/start")
    @PreAuthorize("hasAuthority('app:operate')")
    public ApiResponse<ApplicationDto> start(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.start(id, request));
    }

    @PostMapping("/api/apps/{id}/stop")
    @PreAuthorize("hasAuthority('app:operate')")
    public ApiResponse<ApplicationDto> stop(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.stop(id, request));
    }

    @PostMapping("/api/apps/{id}/restart")
    @PreAuthorize("hasAuthority('app:operate')")
    public ApiResponse<ApplicationDto> restart(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.restart(id, request));
    }

    @GetMapping("/api/apps/{id}/status")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<RuntimeStatus> status(@PathVariable Long id) {
        return ApiResponse.ok(applicationService.getStatus(id));
    }

    @GetMapping("/api/apps/{id}/skills")
    @PreAuthorize("hasAuthority('skill:read')")
    public ApiResponse<com.agentpanel.application.dto.AppSkillsContextDto> appSkills(@PathVariable Long id) {
        return ApiResponse.ok(applicationService.getAppSkillsContext(id));
    }

    @GetMapping(value = "/api/apps/{id}/stats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('app:read')")
    public Flux<ResourceStats> statsStream(@PathVariable Long id) {
        return monitorService.statsStream(id);
    }

    @GetMapping(value = "/api/apps/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('app:read')")
    public Flux<String> logsStream(@PathVariable Long id,
                                   @RequestParam(defaultValue = "true") boolean follow,
                                   @RequestParam(defaultValue = "200") int tail,
                                   @RequestParam(required = false) String since) {
        return monitorService.logsStream(id, new LogOptions(follow, tail, since));
    }

    @GetMapping("/api/apps/{id}/logs/download")
    @PreAuthorize("hasAuthority('app:read')")
    public ResponseEntity<byte[]> downloadLogs(@PathVariable Long id,
                                               @RequestParam(defaultValue = "5000") int tail) {
        byte[] content = monitorService.downloadLogs(id, tail);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=app-" + id + "-logs.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);
    }

    @GetMapping("/api/apps/{id}/files")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<List<AppFileService.FileEntry>> listFiles(@PathVariable Long id,
                                                                 @RequestParam String volume,
                                                                 @RequestParam(required = false) String path) {
        return ApiResponse.ok(appFileService.list(id, volume, path));
    }

    @PostMapping(value = "/api/apps/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<Void> uploadFile(@PathVariable Long id,
                                        @RequestParam String volume,
                                        @RequestParam(required = false) String path,
                                        @RequestParam("file") MultipartFile file) {
        appFileService.upload(id, volume, path, file);
        return ApiResponse.ok();
    }

    @GetMapping("/api/apps/{id}/files/download")
    @PreAuthorize("hasAuthority('app:read')")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id,
                                               @RequestParam String volume,
                                               @RequestParam String path) {
        byte[] content = appFileService.download(id, volume, path);
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @DeleteMapping("/api/apps/{id}/files")
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<Void> deleteFile(@PathVariable Long id,
                                        @RequestParam String volume,
                                        @RequestParam String path) {
        appFileService.delete(id, volume, path);
        return ApiResponse.ok();
    }
}
