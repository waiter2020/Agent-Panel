package com.agentpanel.runtime.docker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DockerHostDataPathResolverTest {

    @Test
    void extractVolumeNameFromDockerVolumePath() {
        assertEquals("deploy_app-data",
                DockerHostDataPathResolver.extractVolumeNameFromPath("/var/lib/docker/volumes/deploy_app-data/_data"));
        assertEquals("deploy_app-data",
                DockerHostDataPathResolver.extractVolumeNameFromPath("/data/docker/volumes/deploy_app-data/_data"));
        assertNull(DockerHostDataPathResolver.extractVolumeNameFromPath("/host/path/data/apps"));
    }
}
