package com.agentpanel.memory.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class SkillReloadNotifyResult {
    private Long skillId;
    private String skillName;
    private Long topologyId;
    private Instant notifiedAt;
    private List<SkillReloadNotifyResult> skills;
}
