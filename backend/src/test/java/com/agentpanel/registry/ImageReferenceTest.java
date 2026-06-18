package com.agentpanel.registry;

import com.agentpanel.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageReferenceTest {

    @Test
    void parsesGhcrImage() {
        ImageReference ref = ImageReference.parse("ghcr.io/openclaw/openclaw");
        assertEquals("ghcr.io", ref.registryHost());
        assertEquals("openclaw/openclaw", ref.repository());
        assertFalse(ref.dockerHub());
        assertEquals("https://ghcr.io", ref.registryBaseUrl());
        assertEquals("/v2/openclaw/openclaw/tags/list", ref.tagsListPath());
    }

    @Test
    void parsesDockerHubLibraryImage() {
        ImageReference ref = ImageReference.parse("nginx");
        assertEquals("registry-1.docker.io", ref.registryHost());
        assertEquals("library/nginx", ref.repository());
        assertTrue(ref.dockerHub());
    }

    @Test
    void parsesDockerHubUserImage() {
        ImageReference ref = ImageReference.parse("user/my-app");
        assertEquals("registry-1.docker.io", ref.registryHost());
        assertEquals("user/my-app", ref.repository());
        assertTrue(ref.dockerHub());
    }

    @Test
    void parsesRegistryWithPort() {
        ImageReference ref = ImageReference.parse("registry.example.com:5000/my/image");
        assertEquals("registry.example.com:5000", ref.registryHost());
        assertEquals("my/image", ref.repository());
    }

    @Test
    void stripTagRemovesTagSuffix() {
        assertEquals("ghcr.io/openclaw/openclaw", ImageReference.stripTag("ghcr.io/openclaw/openclaw:v1.0.0"));
        assertEquals("nginx", ImageReference.stripTag("nginx:latest"));
    }

    @Test
    void stripTagPreservesPortInHost() {
        assertEquals("registry.example.com:5000/my/image",
                ImageReference.stripTag("registry.example.com:5000/my/image:v2"));
    }

    @Test
    void rejectsBlankImage() {
        assertThrows(BusinessException.class, () -> ImageReference.parse("  "));
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(BusinessException.class, () -> ImageReference.parse("bad image"));
    }

    @Test
    void extractRepositoryPathStripsGhcrPrefix() {
        assertEquals("openclaw/openclaw", ImageReference.extractRepositoryPath("ghcr.io/openclaw/openclaw"));
    }

    @Test
    void detectSourceIdForGhcr() {
        assertEquals(ImageReference.SOURCE_GHCR, ImageReference.detectSourceId("ghcr.io/openclaw/openclaw"));
    }

    @Test
    void detectSourceIdForDockerHubShortForm() {
        assertEquals(ImageReference.SOURCE_DOCKERHUB, ImageReference.detectSourceId("openclaw/openclaw"));
    }

    @Test
    void applySourceBuildsGhcrImage() {
        assertEquals("ghcr.io/openclaw/openclaw", ImageReference.applySource(ImageReference.SOURCE_GHCR, "openclaw/openclaw"));
    }

    @Test
    void applySourceBuildsDockerHubImage() {
        assertEquals("openclaw/openclaw", ImageReference.applySource(ImageReference.SOURCE_DOCKERHUB, "openclaw/openclaw"));
    }

    @Test
    void applySourceCustomReturnsRepositoryPathUnchanged() {
        assertEquals("my.registry.local/app", ImageReference.applySource(ImageReference.SOURCE_CUSTOM, "my.registry.local/app"));
    }
}
