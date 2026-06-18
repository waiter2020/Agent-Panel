package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class TopologyDto {
    private Long id;
    private String name;
    private String description;
    private String networkName;
    private String status;
    private Long ownerId;
    private Long inferenceApiKeyId;
    private List<TopologyNodeDto> nodes;
    private List<TopologyLinkDto> links;
    private List<InjectedEnvDto> injectedEnv;
    private List<InjectedSkillDto> injectedSkills;
    private List<MemberAccessUrlDto> memberAccessUrls;
    private boolean needsKeyRedeploy;
    private Instant createdAt;
    private Instant updatedAt;
    private String inferenceKeyRaw;

    @Getter
    @Setter
    public static class TopologyNodeDto {
        private Long id;
        private Long applicationId;
        private String applicationName;
        private String applicationStatus;
        private String role;
        private Map<String, Object> config;
    }
}
