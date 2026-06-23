package com.agentpanel.config;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP clients for upstream Agent runtimes (OpenClaw Gateway, etc.).
 * Forces HTTP/1.1 because several Agent gateways reject HTTP/2 with 400.
 */
public final class AgentUpstreamHttpClients {

    private AgentUpstreamHttpClients() {
    }

    public static HttpClient forProxy() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public static HttpClient forHealthCheck() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
