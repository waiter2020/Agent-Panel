package com.agentpanel.runtime.docker;

import com.agentpanel.runtime.api.DeploySpec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DockerRuntimeProviderTest {

    @Test
    void mergeEnvCombinesPlainAndSecretVariables() {
        DeploySpec spec = new DeploySpec(
                "app-1",
                "nginx",
                "latest",
                Map.of("OLLAMA_BASE_URL", "http://host.docker.internal:11434"),
                Map.of("OPENROUTER_API_KEY", "sk-secret"),
                List.of(),
                List.of(),
                null,
                1,
                Map.of("agentpanel.app", "1")
        );

        Map<String, String> merged = DockerRuntimeProvider.mergeEnv(spec);

        assertEquals("http://host.docker.internal:11434", merged.get("OLLAMA_BASE_URL"));
        assertEquals("sk-secret", merged.get("OPENROUTER_API_KEY"));
        assertEquals(2, merged.size());
    }

    @Test
    void mergeEnvHandlesNullMaps() {
        DeploySpec spec = new DeploySpec(
                "app-1", "nginx", "latest", null, null,
                List.of(), List.of(), null, 1, Map.of());
        assertTrue(DockerRuntimeProvider.mergeEnv(spec).isEmpty());
    }

    @Test
    void mergeEnvSecretOverridesPlainWhenDuplicateKey() {
        DeploySpec spec = new DeploySpec(
                "app-1",
                "nginx",
                "latest",
                Map.of("OPENAI_API_KEY", "plain"),
                Map.of("OPENAI_API_KEY", "secret"),
                List.of(),
                List.of(),
                null,
                1,
                Map.of()
        );
        assertEquals("secret", DockerRuntimeProvider.mergeEnv(spec).get("OPENAI_API_KEY"));
    }
}
