package com.agentpanel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, JwtProperties.class, AgentRuntimeProperties.class, AgentPanelProperties.class, MemoryProperties.class, RegistryProperties.class, OpenClawProperties.class})
public class AppConfig {}
