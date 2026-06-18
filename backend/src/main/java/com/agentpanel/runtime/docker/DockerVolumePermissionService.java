package com.agentpanel.runtime.docker;

import com.agentpanel.config.AgentRuntimeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DockerVolumePermissionService {

    private static final int CHOWN_TIMEOUT_SECONDS = 10;

    private final AgentRuntimeProperties properties;

    public void prepareVolumeDirectory(Path panelPath) {
        AgentRuntimeProperties.Docker docker = properties.getDocker();
        prepareVolumeDirectory(panelPath, docker.getVolumeUid(), docker.getVolumeGid());
    }

    public void prepareVolumeDirectory(Path panelPath, int uid, int gid) {
        try {
            Files.createDirectories(panelPath);
            runOwnershipFix(panelPath, uid, gid);
        } catch (IOException e) {
            log.warn("Failed to create volume directory {}: {}", panelPath, e.getMessage());
        }
    }

    private void runOwnershipFix(Path panelPath, int uid, int gid) {
        String path = panelPath.toString();
        if (runCommand("chown", "-R", uid + ":" + gid, path)) {
            runCommand("chmod", "-R", "u+rwX,g+rwX,o+rwX", path);
            return;
        }
        try {
            Files.setPosixFilePermissions(panelPath, PosixFilePermissions.fromString("rwxrwxrwx"));
            Files.walk(panelPath).forEach(p -> {
                try {
                    Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rwxrwxrwx"));
                } catch (IOException ignored) {
                }
            });
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("Failed to set permissions on volume {}: {}", panelPath, e.getMessage());
        }
    }

    private boolean runCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(CHOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Command timed out: {}", String.join(" ", command));
                return false;
            }
            if (process.exitValue() != 0) {
                log.debug("Command failed (exit {}): {}", process.exitValue(), String.join(" ", command));
                return false;
            }
            return true;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("Command unavailable: {} ({})", String.join(" ", command), e.getMessage());
            return false;
        }
    }
}
