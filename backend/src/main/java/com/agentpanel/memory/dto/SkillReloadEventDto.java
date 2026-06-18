package com.agentpanel.memory.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class SkillReloadEventDto {
    private Long skillId;
    private String skillName;
    private Long topologyId;
    private Instant notifiedAt;
}
