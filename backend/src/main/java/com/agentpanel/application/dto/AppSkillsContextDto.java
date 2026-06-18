package com.agentpanel.application.dto;

import com.agentpanel.memory.dto.SharedSkillDto;
import lombok.Data;

import java.util.List;

@Data
public class AppSkillsContextDto {
    private Long topologyId;
    private String topologyName;
    private List<SharedSkillDto> skills;
    private List<InjectedEnvDto> injectedEnv;
}
