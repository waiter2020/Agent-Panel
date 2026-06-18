package com.agentpanel.runtime.api;

import reactor.core.publisher.Flux;

public interface AgentRuntimeProvider {

    String type();

    DeployResult deploy(DeploySpec spec);

    void start(RuntimeRef ref);

    void stop(RuntimeRef ref);

    void restart(RuntimeRef ref);

    void remove(RuntimeRef ref);

    RuntimeStatus status(RuntimeRef ref);

    ResourceStats stats(RuntimeRef ref);

    Flux<LogLine> logs(RuntimeRef ref, LogOptions options);
}
