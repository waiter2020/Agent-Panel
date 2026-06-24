package com.agentpanel.runtime.docker;

import com.agentpanel.runtime.api.DeploySpec;
import com.agentpanel.runtime.api.RuntimeStatus;
import com.github.dockerjava.api.model.Container;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test
    void isContainerMissingStatusDetectsMissingPhaseAndMessages() {
        assertTrue(DockerRuntimeProvider.isContainerMissingStatus(
                new RuntimeStatus(RuntimeStatus.Phase.MISSING, false, DockerRuntimeProvider.CONTAINER_MISSING_MARKER)));
        assertTrue(DockerRuntimeProvider.isContainerMissingStatus(
                new RuntimeStatus(RuntimeStatus.Phase.ERROR, false,
                        "Status 404: {\"message\":\"No such container: abc\"}")));
        assertFalse(DockerRuntimeProvider.isContainerMissingStatus(
                new RuntimeStatus(RuntimeStatus.Phase.RUNNING, true, "running")));
    }

    @Test
    void pickContainerIdMatchesExpectedName() {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("new-container-id");
        when(container.getNames()).thenReturn(new String[]{"/app-6"});

        Optional<String> found = DockerRuntimeProvider.pickContainerId(List.of(container), "app-6");

        assertTrue(found.isPresent());
        assertEquals("new-container-id", found.get());
    }
}
