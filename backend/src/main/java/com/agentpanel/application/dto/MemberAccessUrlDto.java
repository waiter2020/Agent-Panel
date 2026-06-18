package com.agentpanel.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberAccessUrlDto {
    private Long applicationId;
    private String applicationName;
    private String role;
    private String name;
    private String url;
    private String peerUrl;
}
