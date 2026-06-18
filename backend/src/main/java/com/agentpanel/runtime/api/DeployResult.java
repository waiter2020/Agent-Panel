package com.agentpanel.runtime.api;

import java.util.List;

public record DeployResult(RuntimeRef ref, String status, String message, List<PortMapping> resolvedPorts) {
    public DeployResult(RuntimeRef ref, String status, String message) {
        this(ref, status, message, List.of());
    }
}
