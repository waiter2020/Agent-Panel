package com.agentpanel.registry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistryTagsDto {

    private List<String> tags;
    private String defaultTag;
    private String source;
    private boolean fallback;
    private String message;
}
