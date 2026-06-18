package com.agentpanel.registry;

import com.agentpanel.common.BusinessException;

import java.util.regex.Pattern;

/**
 * Parses a Docker image reference (without tag) into registry host and repository path.
 */
public record ImageReference(String registryHost, String repository, boolean dockerHub) {

    public static final String SOURCE_GHCR = "ghcr";
    public static final String SOURCE_DOCKERHUB = "dockerhub";
    public static final String SOURCE_CUSTOM = "custom";

    private static final String GHCR_HOST = "ghcr.io";
    private static final String DOCKER_HUB_REGISTRY = "registry-1.docker.io";
    private static final Pattern IMAGE_PATTERN = Pattern.compile(
            "^[a-z0-9]+([._-][a-z0-9]+)*(/[a-z0-9]+([._-][a-z0-9]+)*)*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST_PATTERN = Pattern.compile(
            "^[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:[0-9]+)?$",
            Pattern.CASE_INSENSITIVE);

    public static ImageReference parse(String image) {
        if (image == null || image.isBlank()) {
            throw new BusinessException("镜像地址不能为空");
        }
        String ref = image.trim();
        if (ref.length() > 512) {
            throw new BusinessException("镜像地址过长");
        }
        if (ref.contains(" ") || ref.contains("@")) {
            throw new BusinessException("镜像地址格式无效");
        }
        int slash = ref.indexOf('/');
        if (slash < 0) {
            validateName(ref);
            return new ImageReference(DOCKER_HUB_REGISTRY, "library/" + ref, true);
        }
        String first = ref.substring(0, slash);
        String rest = ref.substring(slash + 1);
        if (rest.isBlank()) {
            throw new BusinessException("镜像地址格式无效");
        }
        if (isRegistryHost(first)) {
            validateRepository(rest);
            return new ImageReference(first, rest, isDockerHubHost(first));
        }
        validateRepository(ref);
        return new ImageReference(DOCKER_HUB_REGISTRY, ref, true);
    }

    public static void validateImageFormat(String image) {
        parse(stripTag(image));
    }

    /**
     * Strips registry host prefix and returns repository path (e.g. openclaw/openclaw).
     */
    public static String extractRepositoryPath(String image) {
        String ref = stripTag(image);
        if (ref.isBlank()) {
            return ref;
        }
        if (ref.regionMatches(true, 0, GHCR_HOST + "/", 0, GHCR_HOST.length() + 1)) {
            return ref.substring(GHCR_HOST.length() + 1);
        }
        if (ref.regionMatches(true, 0, "docker.io/", 0, "docker.io/".length())) {
            return ref.substring("docker.io/".length());
        }
        if (ref.regionMatches(true, 0, DOCKER_HUB_REGISTRY + "/", 0, DOCKER_HUB_REGISTRY.length() + 1)) {
            return ref.substring(DOCKER_HUB_REGISTRY.length() + 1);
        }
        int slash = ref.indexOf('/');
        if (slash > 0) {
            String first = ref.substring(0, slash);
            if (isRegistryHost(first)) {
                return ref.substring(slash + 1);
            }
        }
        return ref;
    }

    public static String detectSourceId(String image) {
        String ref = stripTag(image);
        if (ref.isBlank()) {
            return SOURCE_GHCR;
        }
        if (ref.regionMatches(true, 0, GHCR_HOST + "/", 0, GHCR_HOST.length() + 1)
                || GHCR_HOST.equalsIgnoreCase(ref)) {
            return SOURCE_GHCR;
        }
        if (ref.regionMatches(true, 0, "docker.io/", 0, "docker.io/".length())
                || ref.regionMatches(true, 0, DOCKER_HUB_REGISTRY + "/", 0, DOCKER_HUB_REGISTRY.length() + 1)) {
            return SOURCE_DOCKERHUB;
        }
        int slash = ref.indexOf('/');
        if (slash < 0) {
            return SOURCE_DOCKERHUB;
        }
        String first = ref.substring(0, slash);
        if (!isRegistryHost(first)) {
            return SOURCE_DOCKERHUB;
        }
        if (isDockerHubHost(first)) {
            return SOURCE_DOCKERHUB;
        }
        return SOURCE_CUSTOM;
    }

    public static String applySource(String sourceId, String repositoryPath) {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            return repositoryPath;
        }
        String repo = repositoryPath.trim();
        if (SOURCE_CUSTOM.equals(sourceId)) {
            return repo;
        }
        if (SOURCE_DOCKERHUB.equals(sourceId)) {
            if (repo.startsWith("library/")) {
                return repo.substring("library/".length());
            }
            return repo;
        }
        if (SOURCE_GHCR.equals(sourceId)) {
            if (repo.regionMatches(true, 0, GHCR_HOST + "/", 0, GHCR_HOST.length() + 1)) {
                return repo;
            }
            return GHCR_HOST + "/" + repo;
        }
        return repo;
    }

    public static String stripTag(String image) {
        if (image == null) {
            return "";
        }
        String ref = image.trim();
        int at = ref.indexOf('@');
        if (at > 0) {
            ref = ref.substring(0, at);
        }
        int colon = ref.lastIndexOf(':');
        if (colon > 0) {
            String possiblePort = ref.substring(0, colon);
            if (possiblePort.contains("/") || !possiblePort.matches(".*:[0-9]+$")) {
                ref = ref.substring(0, colon);
            }
        }
        return ref;
    }

    public String registryBaseUrl() {
        return "https://" + registryHost;
    }

    public String tagsListPath() {
        return "/v2/" + repository + "/tags/list";
    }

    private static boolean isRegistryHost(String segment) {
        return segment.contains(".") || segment.contains(":") || "localhost".equalsIgnoreCase(segment);
    }

    private static boolean isDockerHubHost(String host) {
        return DOCKER_HUB_REGISTRY.equalsIgnoreCase(host)
                || "docker.io".equalsIgnoreCase(host)
                || "index.docker.io".equalsIgnoreCase(host);
    }

    private static void validateName(String name) {
        if (!IMAGE_PATTERN.matcher(name).matches()) {
            throw new BusinessException("镜像地址格式无效");
        }
    }

    private static void validateRepository(String repository) {
        if (!repository.contains("/")) {
            validateName(repository);
            return;
        }
        String[] parts = repository.split("/");
        for (String part : parts) {
            if (part.isBlank()) {
                throw new BusinessException("镜像地址格式无效");
            }
            validateName(part);
        }
    }

    static boolean isValidRegistryHost(String host) {
        return host != null && HOST_PATTERN.matcher(host).matches();
    }
}
