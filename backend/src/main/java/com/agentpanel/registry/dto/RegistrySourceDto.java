package com.agentpanel.registry.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrySourceDto {

    private String id;
    private String name;
    private String host;
    private String description;
}
