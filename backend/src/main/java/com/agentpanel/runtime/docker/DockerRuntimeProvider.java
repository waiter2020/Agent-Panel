package com.agentpanel.runtime.docker;

import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.api.*;
import com.agentpanel.terminal.TerminalOutputHandler;
import com.agentpanel.terminal.TerminalSession;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DockerRuntimeProvider implements AgentRuntimeProvider {

    private static final int HOST_PORT_MIN = 20000;
    private static final int HOST_PORT_MAX = 30000;
    private static final int DEPLOY_STABILIZE_SECONDS = 5;
    private static final int DEPLOY_LOG_TAIL = 20;

    private final AgentRuntimeProperties properties;
    private final DockerHostDataPathResolver hostDataPathResolver;
    private final DockerVolumePermissionService volumePermissionService;
    private DockerClient dockerClient;

    @PostConstruct
    void init() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(properties.getDocker().getHost())
                .build();
        Duration responseTimeout = properties.getDocker().getResponseTimeout();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(responseTimeout)
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    @Override
    public String type() {
        return "docker";
    }

    @Override
    public DeployResult deploy(DeploySpec spec) {
        try {
            removeExistingContainer(spec.name());
            ensureNetwork(resolveNetwork(spec));
            String fullImage = spec.fullImage();
            ensureImageAvailable(fullImage);
            List<ExposedPort> exposedPorts = new ArrayList<>();
            List<PortBinding> portBindings = new ArrayList<>();
            List<PortMapping> resolvedPorts = new ArrayList<>();
            for (PortMapping port : spec.ports()) {
                ExposedPort exposedPort = ExposedPort.tcp(port.containerPort());
                exposedPorts.add(exposedPort);
                Integer hostPort = port.hostPort();
                if (hostPort == null && port.expose()) {
                    hostPort = allocateHostPort();
                }
                if (hostPort != null) {
                    portBindings.add(new PortBinding(Ports.Binding.bindPort(hostPort), exposedPort));
                }
                resolvedPorts.add(new PortMapping(
                        port.name(), port.containerPort(), hostPort, port.protocol(), port.expose()));
            }
            List<Bind> binds = new ArrayList<>();
            List<Mount> volumeMounts = new ArrayList<>();
            PanelDataMount panelDataMount = hostDataPathResolver.getPanelDataMount();
            Long appId = resolveAppId(spec);
            for (VolumeMount volume : spec.volumes()) {
                if (appId != null) {
                    Path panelPath = hostDataPathResolver.toPanelVolumePath(appId, volume.name());
                    volumePermissionService.prepareVolumeDirectory(panelPath);
                }
                if (panelDataMount.usesNamedVolume() && appId != null) {
                    volumeMounts.add(new Mount()
                            .withType(MountType.VOLUME)
                            .withSource(panelDataMount.volumeName())
                            .withTarget(volume.containerPath())
                            .withVolumeOptions(new SubpathVolumeOptions()
                                    .withSubpath(hostDataPathResolver.toVolumeSubpath(appId, volume.name()))));
                } else {
                    binds.add(new Bind(volume.hostPath(), new Volume(volume.containerPath())));
                }
            }
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withBinds(binds)
                    .withMounts(volumeMounts)
                    .withPortBindings(portBindings)
                    .withRestartPolicy(RestartPolicy.unlessStoppedRestart())
                    .withNetworkMode(resolveNetwork(spec));
            applyResourceLimits(hostConfig, spec.resources());
            Map<String, String> mergedEnv = mergeEnv(spec);
            Map<String, String> containerEnv = new LinkedHashMap<>(filterContainerEnv(mergedEnv));
            var createCmd = dockerClient.createContainerCmd(fullImage)
                    .withName(spec.name())
                    .withEnv(toEnvList(containerEnv))
                    .withExposedPorts(exposedPorts)
                    .withHostConfig(hostConfig)
                    .withLabels(spec.labels());
            applyOpenClawStartupArgs(createCmd, mergedEnv, containerEnv);
            createCmd.withEnv(toEnvList(containerEnv));
            CreateContainerResponse response = createCmd.exec();
            dockerClient.startContainerCmd(response.getId()).exec();
            DeployResult result = evaluateDeployResult(response.getId(), resolvedPorts);
            log.info("Docker 部署成功: name={} image={} containerId={}", spec.name(), fullImage, response.getId());
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Docker 部署失败: name={} image={}", spec.name(), spec.fullImage(), e);
            throw new BusinessException("Docker 部署失败: " + e.getMessage());
        }
    }

    static Map<String, String> mergeEnv(DeploySpec spec) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (spec.env() != null) {
            merged.putAll(spec.env());
        }
        if (spec.secretEnv() != null) {
            merged.putAll(spec.secretEnv());
        }
        return merged;
    }

    static Map<String, String> filterContainerEnv(Map<String, String> merged) {
        if (merged == null || merged.isEmpty()) {
            return Map.of();
        }
        Map<String, String> filtered = new LinkedHashMap<>(merged);
        filtered.remove("OPENCLAW_ALLOW_UNCONFIGURED");
        filtered.remove("OPENCLAW_GATEWAY_MODE");
        return filtered;
    }

    private void applyOpenClawStartupArgs(CreateContainerCmd createCmd, Map<String, String> mergedEnv,
                                          Map<String, String> containerEnv) {
        if (mergedEnv == null) {
            return;
        }
        boolean allowUnconfigured = "true".equalsIgnoreCase(mergedEnv.get("OPENCLAW_ALLOW_UNCONFIGURED"))
                || "local".equalsIgnoreCase(mergedEnv.get("OPENCLAW_GATEWAY_MODE"));
        if (!allowUnconfigured) {
            return;
        }
        createCmd.withCmd("node", "openclaw.mjs", "gateway", "--allow-unconfigured");
    }

    private DeployResult evaluateDeployResult(String containerId, List<PortMapping> resolvedPorts)
            throws InterruptedException {
        TimeUnit.SECONDS.sleep(DEPLOY_STABILIZE_SECONDS);
        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
        InspectContainerResponse.ContainerState state = inspect.getState();
        String status = state.getStatus();
        boolean running = Boolean.TRUE.equals(state.getRunning());
        if (running && !"restarting".equals(status)) {
            return new DeployResult(new RuntimeRef(type(), containerId, null), "running", "容器已启动", resolvedPorts);
        }
        Long exitCode = state.getExitCodeLong();
        String logSnippet = fetchRecentLogs(containerId, DEPLOY_LOG_TAIL);
        String message = "容器启动失败"
                + (exitCode != null ? " (exit " + exitCode + ")" : "")
                + (logSnippet.isBlank() ? "" : ": " + logSnippet);
        return new DeployResult(new RuntimeRef(type(), containerId, null), "error", message, resolvedPorts);
    }

    private String fetchRecentLogs(String containerId, int tail) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tail)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            try {
                                output.write(frame.getPayload());
                            } catch (IOException ignored) {
                            }
                        }
                    })
                    .awaitCompletion(5, TimeUnit.SECONDS);
            return output.toString(StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private Long resolveAppId(DeploySpec spec) {
        if (spec.labels() == null) {
            return null;
        }
        String appId = spec.labels().get("agentpanel.app");
        if (appId == null || appId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(appId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void start(RuntimeRef ref) {
        try {
            dockerClient.startContainerCmd(ref.ref()).exec();
            log.info("Docker 容器已启动: containerId={}", ref.ref());
        } catch (Exception e) {
            log.error("Docker 启动失败: containerId={}", ref.ref(), e);
            throw new BusinessException("Docker 启动失败: " + e.getMessage());
        }
    }

    @Override
    public void stop(RuntimeRef ref) {
        try {
            dockerClient.stopContainerCmd(ref.ref()).withTimeout(10).exec();
            log.info("Docker 容器已停止: containerId={}", ref.ref());
        } catch (Exception e) {
            log.error("Docker 停止失败: containerId={}", ref.ref(), e);
            throw new BusinessException("Docker 停止失败: " + e.getMessage());
        }
    }

    @Override
    public void restart(RuntimeRef ref) {
        try {
            dockerClient.restartContainerCmd(ref.ref()).withTimeout(10).exec();
            log.info("Docker 容器已重启: containerId={}", ref.ref());
        } catch (Exception e) {
            log.error("Docker 重启失败: containerId={}", ref.ref(), e);
            throw new BusinessException("Docker 重启失败: " + e.getMessage());
        }
    }

    @Override
    public void remove(RuntimeRef ref) {
        try {
            try {
                dockerClient.stopContainerCmd(ref.ref()).withTimeout(5).exec();
            } catch (Exception ignored) {
            }
            dockerClient.removeContainerCmd(ref.ref()).withForce(true).exec();
            log.info("Docker 容器已删除: containerId={}", ref.ref());
        } catch (Exception e) {
            log.error("Docker 删除失败: containerId={}", ref.ref(), e);
            throw new BusinessException("Docker 删除失败: " + e.getMessage());
        }
    }

    @Override
    public RuntimeStatus status(RuntimeRef ref) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(ref.ref()).exec();
            String state = inspect.getState().getStatus();
            boolean running = Boolean.TRUE.equals(inspect.getState().getRunning());
            RuntimeStatus.Phase phase = switch (state) {
                case "running" -> RuntimeStatus.Phase.RUNNING;
                case "exited" -> RuntimeStatus.Phase.STOPPED;
                case "created" -> RuntimeStatus.Phase.CREATED;
                default -> running ? RuntimeStatus.Phase.RUNNING : RuntimeStatus.Phase.UNKNOWN;
            };
            return new RuntimeStatus(phase, running, state);
        } catch (Exception e) {
            return new RuntimeStatus(RuntimeStatus.Phase.ERROR, false, e.getMessage());
        }
    }

    @Override
    public ResourceStats stats(RuntimeRef ref) {
        try {
            Statistics[] holder = new Statistics[1];
            dockerClient.statsCmd(ref.ref()).exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    holder[0] = stats;
                    try {
                        close();
                    } catch (IOException ignored) {
                    }
                }
            }).awaitCompletion(3, TimeUnit.SECONDS);
            Statistics stats = holder[0];
            if (stats == null) {
                return ResourceStats.unavailable("Docker stats 无响应");
            }
            long memUsage = stats.getMemoryStats() != null && stats.getMemoryStats().getUsage() != null
                    ? stats.getMemoryStats().getUsage() : 0;
            long memLimit = stats.getMemoryStats() != null && stats.getMemoryStats().getLimit() != null
                    ? stats.getMemoryStats().getLimit() : 0;
            double cpuPercent = calculateCpuPercent(stats);
            long netRx = 0;
            long netTx = 0;
            if (stats.getNetworks() != null) {
                for (var network : stats.getNetworks().values()) {
                    if (network.getRxBytes() != null) {
                        netRx += network.getRxBytes();
                    }
                    if (network.getTxBytes() != null) {
                        netTx += network.getTxBytes();
                    }
                }
            }
            return ResourceStats.ok(cpuPercent, memUsage, memLimit, netRx, netTx, true);
        } catch (Exception e) {
            log.warn("Docker stats 采集失败 containerId={}: {}", ref.ref(), e.getMessage());
            return ResourceStats.unavailable("Docker stats 采集失败: " + e.getMessage());
        }
    }

    public TerminalSession openTerminal(RuntimeRef ref, TerminalOutputHandler handler) {
        try {
            return new DockerTerminalSession(dockerClient, ref.ref(), handler);
        } catch (Exception e) {
            throw new BusinessException("打开 Docker 终端失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<LogLine> logs(RuntimeRef ref, LogOptions options) {
        return Flux.create(sink -> {
            try {
                var cmd = dockerClient.logContainerCmd(ref.ref())
                        .withStdOut(true)
                        .withStdErr(true)
                        .withTail(options.tail() > 0 ? options.tail() : 100);
                if (options.follow()) {
                    cmd.withFollowStream(true);
                }
                if (options.since() != null && !options.since().isBlank()) {
                    int sinceSeconds = parseSinceSeconds(options.since());
                    if (sinceSeconds > 0) {
                        cmd.withSince(sinceSeconds);
                    }
                }
                cmd.exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        sink.next(new LogLine(new String(frame.getPayload()), System.currentTimeMillis()));
                    }

                    @Override
                    public void onComplete() {
                        sink.complete();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        sink.error(throwable);
                    }
                });
            } catch (Exception e) {
                sink.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private void ensureImageAvailable(String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            log.info("本地镜像不存在，开始拉取: {}", image);
            try {
                dockerClient.pullImageCmd(image)
                        .exec(new PullImageResultCallback())
                        .awaitCompletion(5, TimeUnit.MINUTES);
                log.info("镜像拉取完成: {}", image);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("拉取镜像被中断: {}", image, ie);
                throw new BusinessException("拉取镜像被中断: " + image);
            } catch (Exception pullEx) {
                log.error("拉取镜像失败: {}", image, pullEx);
                throw new BusinessException("拉取镜像失败: " + image + " - " + pullEx.getMessage());
            }
        }
    }

    private void removeExistingContainer(String name) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(List.of(name))
                    .exec();
            for (Container container : containers) {
                try {
                    dockerClient.stopContainerCmd(container.getId()).withTimeout(5).exec();
                } catch (Exception ignored) {
                }
                dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
            }
        } catch (Exception ignored) {
        }
    }

    private int allocateHostPort() throws IOException {
        Set<Integer> usedPorts = new HashSet<>();
        for (Container container : dockerClient.listContainersCmd().withShowAll(true).exec()) {
            if (container.getPorts() == null) {
                continue;
            }
            for (ContainerPort port : container.getPorts()) {
                if (port.getPublicPort() != null) {
                    usedPorts.add(port.getPublicPort());
                }
            }
        }
        for (int port = HOST_PORT_MIN; port <= HOST_PORT_MAX; port++) {
            if (!usedPorts.contains(port) && isPortAvailable(port)) {
                return port;
            }
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void applyResourceLimits(HostConfig hostConfig, ResourceLimits limits) {
        if (limits == null) {
            return;
        }
        long memoryBytes = parseMemory(limits.memory());
        if (memoryBytes > 0) {
            hostConfig.withMemory(memoryBytes);
        }
        double cpu = parseCpu(limits.cpu());
        if (cpu > 0) {
            hostConfig.withCpuQuota((long) (cpu * 100_000));
            hostConfig.withCpuPeriod(100_000L);
        }
    }

    private long parseMemory(String memory) {
        if (memory == null || memory.isBlank()) {
            return 0;
        }
        String value = memory.trim().toUpperCase();
        try {
            if (value.endsWith("GI")) {
                return (long) (Double.parseDouble(value.substring(0, value.length() - 2)) * 1024 * 1024 * 1024);
            }
            if (value.endsWith("MI")) {
                return (long) (Double.parseDouble(value.substring(0, value.length() - 2)) * 1024 * 1024);
            }
            if (value.endsWith("G")) {
                return (long) (Double.parseDouble(value.substring(0, value.length() - 1)) * 1000 * 1000 * 1000);
            }
            if (value.endsWith("M")) {
                return (long) (Double.parseDouble(value.substring(0, value.length() - 1)) * 1000 * 1000);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseCpu(String cpu) {
        if (cpu == null || cpu.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(cpu.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double calculateCpuPercent(Statistics stats) {
        if (stats.getCpuStats() == null || stats.getPreCpuStats() == null) {
            return 0;
        }
        var cpuStats = stats.getCpuStats();
        var preCpuStats = stats.getPreCpuStats();
        if (cpuStats.getCpuUsage() == null || preCpuStats.getCpuUsage() == null) {
            return 0;
        }
        Long currentTotal = cpuStats.getCpuUsage().getTotalUsage();
        Long preTotal = preCpuStats.getCpuUsage().getTotalUsage();
        Long currentSystem = cpuStats.getSystemCpuUsage();
        Long preSystem = preCpuStats.getSystemCpuUsage();
        if (currentTotal == null || preTotal == null || currentSystem == null || preSystem == null) {
            return 0;
        }
        long cpuDelta = currentTotal - preTotal;
        long systemDelta = currentSystem - preSystem;
        if (cpuDelta < 0 || systemDelta <= 0) {
            return 0;
        }
        int onlineCpus = cpuStats.getOnlineCpus() != null ? cpuStats.getOnlineCpus().intValue() : 1;
        return (cpuDelta / (double) systemDelta) * onlineCpus * 100.0;
    }

    public void ensureNetwork(String networkName) {
        try {
            doEnsureNetwork(networkName);
        } catch (Exception e) {
            throw new BusinessException("创建 Docker 网络失败: " + e.getMessage());
        }
    }

    public void connectToNetwork(String containerId, String networkName) {
        try {
            doEnsureNetwork(networkName);
            String networkId = dockerClient.listNetworksCmd().withNameFilter(networkName).exec().stream()
                    .findFirst()
                    .map(Network::getId)
                    .orElseThrow(() -> new BusinessException("Docker 网络不存在: " + networkName));
            dockerClient.connectToNetworkCmd()
                    .withContainerId(containerId)
                    .withNetworkId(networkId)
                    .exec();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("连接 Docker 网络失败: " + e.getMessage());
        }
    }

    private String resolveNetwork(DeploySpec spec) {
        if (spec.network() != null && !spec.network().isBlank()) {
            return spec.network();
        }
        return properties.getDocker().getNetwork();
    }

    private void doEnsureNetwork(String network) throws IOException, InterruptedException {
        List<Network> networks = dockerClient.listNetworksCmd().withNameFilter(network).exec();
        if (networks.isEmpty()) {
            dockerClient.createNetworkCmd().withName(network).withDriver("bridge").exec();
        }
    }

    private int parseSinceSeconds(String since) {
        try {
            if (since.matches("\\d+")) {
                long epoch = Long.parseLong(since);
                if (epoch > 1_000_000_000_000L) {
                    epoch /= 1000;
                }
                return (int) Math.max(0, (System.currentTimeMillis() / 1000) - epoch);
            }
            long epoch = java.time.Instant.parse(since).getEpochSecond();
            return (int) Math.max(0, (System.currentTimeMillis() / 1000) - epoch);
        } catch (Exception e) {
            return 0;
        }
    }

    private List<String> toEnvList(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return List.of();
        }
        return env.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList();
    }
}
