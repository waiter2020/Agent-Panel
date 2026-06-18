package com.agentpanel.runtime.docker;

import com.agentpanel.config.AgentRuntimeProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DockerHostDataPathResolver {

    private static final Pattern DOCKER_VOLUME_PATH = Pattern.compile("/volumes/([^/]+)/_data/?$");

    private final AgentRuntimeProperties properties;
    private DockerClient dockerClient;
    private volatile PanelDataMount cachedPanelDataMount;

    @PostConstruct
    void init() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(properties.getDocker().getHost())
                .build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(10)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(properties.getDocker().getResponseTimeout())
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    public PanelDataMount getPanelDataMount() {
        if (cachedPanelDataMount != null) {
            return cachedPanelDataMount;
        }
        synchronized (this) {
            if (cachedPanelDataMount != null) {
                return cachedPanelDataMount;
            }
            cachedPanelDataMount = detectPanelDataMount();
            log.info("Docker panel data mount resolved: volumeName={}, bindHostRoot={}",
                    cachedPanelDataMount.volumeName(), cachedPanelDataMount.bindHostRoot());
            return cachedPanelDataMount;
        }
    }

    public String getHostDataRoot() {
        String configured = properties.getDocker().getHostDataRoot();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String hostRoot = getPanelDataMount().hostRoot();
        if (hostRoot != null && !hostRoot.isBlank()) {
            return hostRoot;
        }
        return properties.getDocker().getDataRoot();
    }

    public String toHostVolumePath(long appId, String volumeName) {
        return Path.of(getHostDataRoot(), String.valueOf(appId), volumeName).toString();
    }

    public String toVolumeSubpath(long appId, String volumeName) {
        return Path.of(String.valueOf(appId), volumeName).toString().replace('\\', '/');
    }

    public Path toPanelVolumePath(long appId, String volumeName) {
        return Path.of(properties.getDocker().getDataRoot(), String.valueOf(appId), volumeName);
    }

    private PanelDataMount detectPanelDataMount() {
        String dataRoot = properties.getDocker().getDataRoot();
        String configured = properties.getDocker().getHostDataRoot();
        if (configured != null && !configured.isBlank()) {
            String volumeName = extractVolumeNameFromPath(configured);
            return new PanelDataMount(volumeName, configured.trim());
        }
        String configuredVolume = properties.getDocker().getDataVolumeName();
        if (configuredVolume != null && !configuredVolume.isBlank()) {
            return new PanelDataMount(configuredVolume.trim(), null);
        }
        List<String> candidates = List.of(
                System.getenv("HOSTNAME"),
                "agent-panel"
        );
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                InspectContainerResponse inspect = dockerClient.inspectContainerCmd(candidate).exec();
                if (inspect.getMounts() == null) {
                    continue;
                }
                for (InspectContainerResponse.Mount mount : inspect.getMounts()) {
                    if (!dataRoot.equals(mount.getDestination()) || mount.getSource() == null) {
                        continue;
                    }
                    String volumeName = mount.getName();
                    if (volumeName == null || volumeName.isBlank()) {
                        volumeName = extractVolumeNameFromPath(mount.getSource());
                    }
                    if (volumeName != null && !volumeName.isBlank()) {
                        return new PanelDataMount(volumeName, mount.getSource());
                    }
                    return new PanelDataMount(null, mount.getSource());
                }
            } catch (Exception e) {
                log.debug("Could not inspect container {} for data mount: {}", candidate, e.getMessage());
            }
        }
        String fromMountInfo = detectBindRootFromMountInfo(dataRoot);
        if (fromMountInfo != null && !fromMountInfo.isBlank()) {
            String volumeName = extractVolumeNameFromPath(fromMountInfo);
            return new PanelDataMount(volumeName, fromMountInfo);
        }
        log.warn("Unable to detect Docker data mount, falling back to data-root path: {}", dataRoot);
        return new PanelDataMount(null, dataRoot);
    }

    static String extractVolumeNameFromPath(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        Matcher matcher = DOCKER_VOLUME_PATH.matcher(source.replace('\\', '/'));
        return matcher.find() ? matcher.group(1) : null;
    }

    private String detectBindRootFromMountInfo(String dataRoot) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/mountinfo"))) {
                String[] parts = line.split(" ");
                if (parts.length < 5 || !dataRoot.equals(parts[4])) {
                    continue;
                }
                String root = parts[3];
                if (root != null && !root.isBlank() && !"/".equals(root)) {
                    return root;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to read /proc/self/mountinfo: {}", e.getMessage());
        }
        return null;
    }
}
