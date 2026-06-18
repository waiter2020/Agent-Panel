package com.agentpanel.application.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ApplicationDto {
    private Long id;
    private String name;
    private Long templateId;
    private String templateName;
    private Long ownerId;
    private String image;
    private String tag;
    private String status;
    private List<Map<String, Object>> ports;
    private Map<String, Object> resources;
    private List<Map<String, Object>> volumes;
    private int replicas;
    private String runtimeProvider;
    private String remark;
    private String runtimeRef;
    private String runtimeNamespace;
    private List<EnvItem> env;
    private List<Map<String, Object>> envSchema;
    private List<Map<String, Object>> accessUrls;

    @Data
    public static class EnvItem {
        private String key;
        private String value;
        private boolean secret;
    }
}
