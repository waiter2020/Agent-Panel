package com.agentpanel.config;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentUpstreamHttpClientsTest {

    @Test
    void forProxyUsesHttp11() {
        HttpClient client = AgentUpstreamHttpClients.forProxy();
        assertEquals(HttpClient.Version.HTTP_1_1, client.version());
    }

    @Test
    void forHealthCheckUsesHttp11() {
        HttpClient client = AgentUpstreamHttpClients.forHealthCheck();
        assertEquals(HttpClient.Version.HTTP_1_1, client.version());
    }
}
