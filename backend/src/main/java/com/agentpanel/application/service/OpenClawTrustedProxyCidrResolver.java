package com.agentpanel.application.service;

import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.config.OpenClawProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves gateway.trustedProxies CIDRs for OpenClaw trusted-proxy auth.
 * Discovers Panel proxy source networks at deploy time instead of hard-coding Docker bridge CIDRs.
 */
@Slf4j
@Component
public class OpenClawTrustedProxyCidrResolver {

    private static final List<String> LOOPBACK_CIDRS = List.of("127.0.0.1/32", "::1/128");

    private final AgentRuntimeProperties runtimeProperties;
    private final OpenClawProperties openClawProperties;
    private volatile DockerClient dockerClient;

    public OpenClawTrustedProxyCidrResolver(AgentRuntimeProperties runtimeProperties,
                                          OpenClawProperties openClawProperties) {
        this.runtimeProperties = runtimeProperties;
        this.openClawProperties = openClawProperties;
    }

    public List<String> resolve(String runtimeProvider, String deployNetworkName) {
        LinkedHashSet<String> cidrs = new LinkedHashSet<>();
        cidrs.addAll(LOOPBACK_CIDRS);
        addConfiguredExtras(cidrs, runtimeProvider);
        if ("k8s".equals(runtimeProvider)) {
            addK8sCidrs(cidrs);
        } else {
            addDockerCidrs(cidrs, deployNetworkName);
        }
        if (cidrs.size() <= LOOPBACK_CIDRS.size()) {
            log.warn("OpenClaw trustedProxies discovery found no runtime CIDRs provider={} network={}; "
                            + "using configured fallback CIDRs only",
                    runtimeProvider, deployNetworkName);
        } else {
            log.info("OpenClaw trustedProxies resolved provider={} network={} cidrs={}",
                    runtimeProvider, deployNetworkName, cidrs);
        }
        return List.copyOf(cidrs);
    }

    private void addConfiguredExtras(Set<String> cidrs, String runtimeProvider) {
        if (openClawProperties.getTrustedProxies() != null) {
            for (String cidr : openClawProperties.getTrustedProxies()) {
                if (cidr != null && !cidr.isBlank()) {
                    cidrs.add(cidr.trim());
                }
            }
        }
        if ("k8s".equals(runtimeProvider) && openClawProperties.getK8s() != null
                && openClawProperties.getK8s().getTrustedProxyCidrs() != null) {
            for (String cidr : openClawProperties.getK8s().getTrustedProxyCidrs()) {
                if (cidr != null && !cidr.isBlank()) {
                    cidrs.add(cidr.trim());
                }
            }
        }
    }

    private void addDockerCidrs(Set<String> cidrs, String deployNetworkName) {
        DockerClient client = dockerClient();
        if (client == null) {
            return;
        }
        LinkedHashSet<String> networkNames = new LinkedHashSet<>();
        if (deployNetworkName != null && !deployNetworkName.isBlank()) {
            networkNames.add(deployNetworkName.trim());
        } else {
            networkNames.add(runtimeProperties.getDocker().getNetwork());
        }
        networkNames.addAll(discoverPanelNetworkNames(client));
        for (String networkName : networkNames) {
            addDockerNetworkSubnets(client, cidrs, networkName);
        }
    }

    private void addK8sCidrs(Set<String> cidrs) {
        String podIp = System.getenv("POD_IP");
        if (podIp != null && !podIp.isBlank()) {
            cidrs.add(podIp.trim() + "/32");
        }
        if (openClawProperties.getK8s() == null || !openClawProperties.getK8s().isAutoDiscoverNodePodCidrs()) {
            return;
        }
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            List<Node> nodes = client.nodes().list().getItems();
            for (Node node : nodes) {
                if (node.getSpec() == null) {
                    continue;
                }
                String podCidr = node.getSpec().getPodCIDR();
                if (podCidr != null && !podCidr.isBlank()) {
                    cidrs.add(podCidr.trim());
                }
                List<String> podCidrs = node.getSpec().getPodCIDRs();
                if (podCidrs != null) {
                    for (String cidr : podCidrs) {
                        if (cidr != null && !cidr.isBlank()) {
                            cidrs.add(cidr.trim());
                        }
                    }
                }
            }
            log.debug("Discovered {} K8s node pod CIDR entries for trustedProxies", nodes.size());
        } catch (Exception e) {
            log.warn("Failed to auto-discover K8s node pod CIDRs for trustedProxies: {}", e.getMessage());
        }
    }

    private Set<String> discoverPanelNetworkNames(DockerClient client) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        List<String> hostCandidates = new ArrayList<>();
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            hostCandidates.add(hostname.trim());
        }
        hostCandidates.add("agent-panel");
        for (String candidate : hostCandidates) {
            try {
                var inspect = client.inspectContainerCmd(candidate).exec();
                Map<String, ContainerNetwork> networks = inspect.getNetworkSettings() != null
                        ? inspect.getNetworkSettings().getNetworks()
                        : null;
                if (networks != null) {
                    names.addAll(networks.keySet());
                }
                break;
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return names;
    }

    private void addDockerNetworkSubnets(DockerClient client, Set<String> cidrs, String networkName) {
        try {
            List<Network> networks = client.listNetworksCmd().withNameFilter(networkName).exec();
            if (networks.isEmpty()) {
                log.debug("Docker network not found for trustedProxies discovery: {}", networkName);
                return;
            }
            for (Network network : networks) {
                var ipam = network.getIpam();
                if (ipam == null || ipam.getConfig() == null) {
                    continue;
                }
                ipam.getConfig().forEach(config -> {
                    if (config.getSubnet() != null && !config.getSubnet().isBlank()) {
                        cidrs.add(config.getSubnet().trim());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Failed to read Docker network {} for trustedProxies: {}", networkName, e.getMessage());
        }
    }

    private DockerClient dockerClient() {
        if (dockerClient != null) {
            return dockerClient;
        }
        synchronized (this) {
            if (dockerClient != null) {
                return dockerClient;
            }
            try {
                var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .withDockerHost(runtimeProperties.getDocker().getHost())
                        .build();
                Duration responseTimeout = runtimeProperties.getDocker().getResponseTimeout();
                var httpClient = new ApacheDockerHttpClient.Builder()
                        .dockerHost(config.getDockerHost())
                        .maxConnections(10)
                        .connectionTimeout(Duration.ofSeconds(5))
                        .responseTimeout(responseTimeout)
                        .build();
                dockerClient = DockerClientImpl.getInstance(config, httpClient);
                dockerClient.pingCmd().exec();
                return dockerClient;
            } catch (Exception e) {
                log.debug("Docker API unavailable for trustedProxies discovery: {}", e.getMessage());
                return null;
            }
        }
    }

    @PreDestroy
    void shutdown() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception ignored) {
            }
        }
    }
}
