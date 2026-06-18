package com.agentpanel.runtime;

import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.api.AgentRuntimeProvider;
import com.agentpanel.runtime.docker.DockerRuntimeProvider;
import com.agentpanel.runtime.k8s.K8sRuntimeProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RuntimeProviderFactory {

    private final Map<String, AgentRuntimeProvider> providers;
    private final AgentRuntimeProperties properties;

    public RuntimeProviderFactory(List<AgentRuntimeProvider> providerList, AgentRuntimeProperties properties) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(AgentRuntimeProvider::type, Function.identity()));
        this.properties = properties;
    }

    public AgentRuntimeProvider get(String type) {
        String providerType = type != null && !type.isBlank() ? type : properties.getProvider();
        AgentRuntimeProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported runtime provider: " + providerType);
        }
        return provider;
    }

    public AgentRuntimeProvider getDefault() {
        return get(properties.getProvider());
    }
}
