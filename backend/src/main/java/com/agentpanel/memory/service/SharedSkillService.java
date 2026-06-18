package com.agentpanel.memory.service;

import com.agentpanel.application.repository.AgentTopologyRepository;
import com.agentpanel.application.repository.ApplicationRepository;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.common.BusinessException;
import com.agentpanel.memory.dto.CreateSkillRequest;
import com.agentpanel.memory.dto.SharedSkillDto;
import com.agentpanel.memory.dto.SkillReloadEventDto;
import com.agentpanel.memory.dto.SkillReloadNotifyResult;
import com.agentpanel.memory.dto.UpdateSkillRequest;
import com.agentpanel.memory.entity.SharedSkill;
import com.agentpanel.memory.repository.SharedSkillRepository;
import com.agentpanel.storage.StorageService;
import com.agentpanel.system.entity.AuditLog;
import com.agentpanel.system.repository.AuditLogRepository;
import com.agentpanel.system.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class SharedSkillService {

    private final SharedSkillRepository sharedSkillRepository;
    private final AgentTopologyRepository topologyRepository;
    private final ApplicationRepository applicationRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;

    public List<SharedSkillDto> listByTopology(Long topologyId) {
        ensureTopology(topologyId);
        return sharedSkillRepository.findByTopologyIdOrderByNameAsc(topologyId).stream()
                .map(this::toDto)
                .toList();
    }

    public SharedSkillDto get(Long id) {
        return toDto(findSkill(id));
    }

    @Transactional
    public SharedSkillDto create(CreateSkillRequest request, MultipartFile file) {
        if (request.getTopologyId() == null) {
            throw new BusinessException("请选择拓扑");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("技能名称不能为空");
        }
        ensureTopology(request.getTopologyId());
        if (request.getApplicationId() != null) {
            ensureApplication(request.getApplicationId());
        }
        if (sharedSkillRepository.existsByTopologyIdAndName(request.getTopologyId(), request.getName().trim())) {
            throw new BusinessException("该拓扑下已存在同名技能");
        }

        SharedSkill skill = new SharedSkill();
        skill.setTopologyId(request.getTopologyId());
        skill.setApplicationId(request.getApplicationId());
        skill.setName(request.getName().trim());
        skill.setDescription(request.getDescription());
        skill.setContent(request.getContent());
        skill.setMetadata(request.getMetadata() == null ? Map.of() : request.getMetadata());
        if (file != null && !file.isEmpty()) {
            skill.setFilePath(uploadSkillFile(request.getTopologyId(), request.getName().trim(), file));
        }
        return toDto(sharedSkillRepository.save(skill));
    }

    @Transactional
    public SharedSkillDto update(Long id, UpdateSkillRequest request, MultipartFile file) {
        SharedSkill skill = findSkill(id);
        if (request.getName() != null && !request.getName().isBlank()
                && !request.getName().trim().equals(skill.getName())
                && sharedSkillRepository.existsByTopologyIdAndName(skill.getTopologyId(), request.getName().trim())) {
            throw new BusinessException("该拓扑下已存在同名技能");
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            skill.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            skill.setDescription(request.getDescription());
        }
        if (request.getContent() != null) {
            skill.setContent(request.getContent());
        }
        if (request.getApplicationId() != null) {
            ensureApplication(request.getApplicationId());
            skill.setApplicationId(request.getApplicationId());
        }
        if (request.getMetadata() != null) {
            skill.setMetadata(request.getMetadata());
        }
        if (file != null && !file.isEmpty()) {
            if (skill.getFilePath() != null) {
                try {
                    storageService.delete(skill.getFilePath());
                } catch (Exception ignored) {
                    // best effort cleanup
                }
            }
            skill.setFilePath(uploadSkillFile(skill.getTopologyId(), skill.getName(), file));
        }
        return toDto(sharedSkillRepository.save(skill));
    }

    @Transactional
    public void delete(Long id) {
        SharedSkill skill = findSkill(id);
        if (skill.getFilePath() != null) {
            try {
                storageService.delete(skill.getFilePath());
            } catch (Exception ignored) {
                // best effort cleanup
            }
        }
        sharedSkillRepository.delete(skill);
    }

    public URL downloadUrl(Long id) {
        SharedSkill skill = findSkill(id);
        if (skill.getFilePath() == null || skill.getFilePath().isBlank()) {
            throw new BusinessException("该技能没有关联文件");
        }
        return storageService.presignedGetUrl(skill.getFilePath(), Duration.ofMinutes(15));
    }

    @Transactional
    public SkillReloadNotifyResult notifyReload(Long id, HttpServletRequest request) {
        SharedSkill skill = findSkill(id);
        Instant notifiedAt = Instant.now();
        auditService.log(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUsername(),
                "skill_reload_notify",
                "skill",
                String.valueOf(id),
                Map.of("topologyId", skill.getTopologyId(), "skillName", skill.getName()),
                request != null ? request.getRemoteAddr() : null,
                "success");
        SkillReloadNotifyResult result = new SkillReloadNotifyResult();
        result.setSkillId(skill.getId());
        result.setSkillName(skill.getName());
        result.setTopologyId(skill.getTopologyId());
        result.setNotifiedAt(notifiedAt);
        return result;
    }

    public SkillReloadNotifyResult notifyReloadForTopology(Long topologyId, HttpServletRequest request) {
        ensureTopology(topologyId);
        List<SkillReloadNotifyResult> skills = sharedSkillRepository.findByTopologyIdOrderByNameAsc(topologyId).stream()
                .map(skill -> notifyReload(skill.getId(), request))
                .toList();
        SkillReloadNotifyResult batch = new SkillReloadNotifyResult();
        batch.setTopologyId(topologyId);
        batch.setNotifiedAt(Instant.now());
        batch.setSkills(skills);
        return batch;
    }

    public List<SkillReloadEventDto> listReloadEvents(Long topologyId, Instant since) {
        ensureTopology(topologyId);
        Instant effectiveSince = since != null ? since : Instant.now().minus(Duration.ofHours(24));
        return auditLogRepository.findSkillReloadEventsSince(topologyId, effectiveSince).stream()
                .map(this::toReloadEventDto)
                .toList();
    }

    public Flux<SkillReloadEventDto> reloadEventsStream(Long topologyId, Instant since) {
        ensureTopology(topologyId);
        AtomicReference<Instant> cursor = new AtomicReference<>(since != null ? since : Instant.now());
        return Flux.interval(Duration.ofSeconds(3))
                .flatMapIterable(tick -> {
                    List<SkillReloadEventDto> events = auditLogRepository
                            .findSkillReloadEventsSince(topologyId, cursor.get()).stream()
                            .map(this::toReloadEventDto)
                            .toList();
                    events.stream()
                            .map(SkillReloadEventDto::getNotifiedAt)
                            .max(Instant::compareTo)
                            .ifPresent(cursor::set);
                    return events;
                })
                .onErrorResume(e -> Flux.empty());
    }

    private SkillReloadEventDto toReloadEventDto(AuditLog log) {
        SkillReloadEventDto dto = new SkillReloadEventDto();
        dto.setSkillId(Long.parseLong(log.getResourceId()));
        dto.setNotifiedAt(log.getAt());
        if (log.getDetail() != null) {
            Object topologyId = log.getDetail().get("topologyId");
            if (topologyId instanceof Number number) {
                dto.setTopologyId(number.longValue());
            }
            Object skillName = log.getDetail().get("skillName");
            if (skillName != null) {
                dto.setSkillName(String.valueOf(skillName));
            }
        }
        return dto;
    }

    private String uploadSkillFile(Long topologyId, String skillName, MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new BusinessException("文件名无效");
        }
        String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "skills/" + topologyId + "/" + skillName.replaceAll("[^a-zA-Z0-9._-]", "_") + "/" + safeName;
        try {
            storageService.putObject(key, file.getInputStream(), file.getSize(),
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        } catch (IOException e) {
            throw new BusinessException("上传技能文件失败: " + e.getMessage());
        }
        return key;
    }

    private SharedSkill findSkill(Long id) {
        return sharedSkillRepository.findById(id)
                .orElseThrow(() -> new BusinessException("技能不存在"));
    }

    private void ensureTopology(Long topologyId) {
        topologyRepository.findByIdAndDeletedFalse(topologyId)
                .orElseThrow(() -> new BusinessException("拓扑不存在"));
    }

    private void ensureApplication(Long applicationId) {
        applicationRepository.findByIdAndDeletedFalse(applicationId)
                .orElseThrow(() -> new BusinessException("应用不存在"));
    }

    private SharedSkillDto toDto(SharedSkill skill) {
        SharedSkillDto dto = new SharedSkillDto();
        dto.setId(skill.getId());
        dto.setTopologyId(skill.getTopologyId());
        topologyRepository.findByIdAndDeletedFalse(skill.getTopologyId())
                .ifPresent(t -> dto.setTopologyName(t.getName()));
        dto.setApplicationId(skill.getApplicationId());
        if (skill.getApplicationId() != null) {
            applicationRepository.findByIdAndDeletedFalse(skill.getApplicationId())
                    .ifPresent(a -> dto.setApplicationName(a.getName()));
        }
        dto.setName(skill.getName());
        dto.setDescription(skill.getDescription());
        dto.setContent(skill.getContent());
        dto.setFilePath(skill.getFilePath());
        dto.setMetadata(skill.getMetadata());
        dto.setCreatedAt(skill.getCreatedAt());
        dto.setUpdatedAt(skill.getUpdatedAt());
        return dto;
    }
}
