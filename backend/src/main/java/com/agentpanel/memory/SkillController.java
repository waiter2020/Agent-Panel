package com.agentpanel.memory;

import com.agentpanel.common.ApiResponse;
import com.agentpanel.memory.dto.CreateSkillRequest;
import com.agentpanel.memory.dto.SharedSkillDto;
import com.agentpanel.memory.dto.SkillReloadEventDto;
import com.agentpanel.memory.dto.SkillReloadNotifyResult;
import com.agentpanel.memory.dto.UpdateSkillRequest;
import com.agentpanel.memory.service.SharedSkillService;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SharedSkillService sharedSkillService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('skill:read')")
    public ApiResponse<List<SharedSkillDto>> list(@RequestParam Long topologyId) {
        return ApiResponse.ok(sharedSkillService.listByTopology(topologyId));
    }

    @GetMapping("/reload-events")
    @PreAuthorize("hasAuthority('skill:read')")
    public ApiResponse<List<SkillReloadEventDto>> reloadEvents(
            @RequestParam Long topologyId,
            @RequestParam(required = false) Instant since) {
        return ApiResponse.ok(sharedSkillService.listReloadEvents(topologyId, since));
    }

    @GetMapping(value = "/reload-events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('skill:read')")
    public Flux<SkillReloadEventDto> reloadEventsStream(
            @RequestParam Long topologyId,
            @RequestParam(required = false) Instant since) {
        return sharedSkillService.reloadEventsStream(topologyId, since);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('skill:read')")
    public ApiResponse<SharedSkillDto> get(@PathVariable Long id) {
        return ApiResponse.ok(sharedSkillService.get(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('skill:write')")
    public ApiResponse<SharedSkillDto> create(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "file", required = false) MultipartFile file) throws Exception {
        CreateSkillRequest request = objectMapper.readValue(dataJson, CreateSkillRequest.class);
        return ApiResponse.ok(sharedSkillService.create(request, file));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('skill:write')")
    public ApiResponse<SharedSkillDto> createJson(@RequestBody CreateSkillRequest request) {
        return ApiResponse.ok(sharedSkillService.create(request, null));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('skill:write')")
    public ApiResponse<SharedSkillDto> update(
            @PathVariable Long id,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "file", required = false) MultipartFile file) throws Exception {
        UpdateSkillRequest request = objectMapper.readValue(dataJson, UpdateSkillRequest.class);
        return ApiResponse.ok(sharedSkillService.update(id, request, file));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('skill:write')")
    public ApiResponse<SharedSkillDto> updateJson(@PathVariable Long id, @RequestBody UpdateSkillRequest request) {
        return ApiResponse.ok(sharedSkillService.update(id, request, null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('skill:write')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sharedSkillService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('skill:read')")
    public ApiResponse<Map<String, String>> download(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("url", sharedSkillService.downloadUrl(id).toString()));
    }

    @PostMapping("/{id}/notify-reload")
    @PreAuthorize("hasAuthority('skill:write')")
    public ApiResponse<SkillReloadNotifyResult> notifyReload(
            @PathVariable Long id,
            HttpServletRequest request) {
        return ApiResponse.ok(sharedSkillService.notifyReload(id, request));
    }
}
