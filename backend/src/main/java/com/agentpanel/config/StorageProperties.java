package com.agentpanel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        String type,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        boolean pathStyleAccess
) {}
