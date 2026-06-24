package com.agentpanel.runtime.k8s;

import com.agentpanel.application.service.OpenClawGatewayBootstrapService;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.api.*;
import com.agentpanel.runtime.docker.DockerRuntimeProvider;
import com.agentpanel.terminal.TerminalOutputHandler;
import com.agentpanel.terminal.TerminalSession;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class K8sRuntimeProvider implements AgentRuntimeProvider {

    private final AgentRuntimeProperties properties;
    private final KubeletStatsHelper kubeletStatsHelper;
    private final OpenClawGatewayBootstrapService openClawGatewayBootstrapService;

    private KubernetesClient client() {
        if (properties.getK8s().isInCluster()) {
            return new KubernetesClientBuilder().build();
        }
        return new KubernetesClientBuilder().build();
    }

    @Override
    public String type() {
        return "k8s";
    }

    @Override
    public DeployResult deploy(DeploySpec spec) {
        String namespace = spec.namespace() != null && !spec.namespace().isBlank()
                ? spec.namespace() : properties.getK8s().getNamespace();
        String name = spec.name();
        try (KubernetesClient client = client()) {
            ensureNamespace(client, namespace);
            String secretName = name + "-env";
            if (spec.secretEnv() != null && !spec.secretEnv().isEmpty()) {
                Secret secret = new SecretBuilder()
                        .withNewMetadata().withName(secretName).withNamespace(namespace).endMetadata()
                        .withStringData(spec.secretEnv())
                        .build();
                client.resource(secret).inNamespace(namespace).serverSideApply();
            }
            List<ContainerPort> containerPorts = spec.ports().stream()
                    .map(p -> new ContainerPortBuilder().withContainerPort(p.containerPort()).withName(p.name()).build())
                    .collect(Collectors.toList());
            List<EnvVar> envVars = new ArrayList<>();
            if (spec.env() != null) {
                spec.env().forEach((k, v) -> envVars.add(new EnvVarBuilder().withName(k).withValue(v).build()));
            }
            if (spec.secretEnv() != null) {
                spec.secretEnv().keySet().forEach(k -> envVars.add(new EnvVarBuilder()
                        .withName(k)
                        .withNewValueFrom()
                        .withNewSecretKeyRef().withName(secretName).withKey(k).endSecretKeyRef()
                        .endValueFrom()
                        .build()));
            }
            List<io.fabric8.kubernetes.api.model.VolumeMount> volumeMounts = spec.volumes().stream()
                    .map(v -> new VolumeMountBuilder().withName(v.name()).withMountPath(v.containerPath()).build())
                    .collect(Collectors.toList());
            List<Volume> volumes = spec.volumes().stream()
                    .map(v -> buildVolume(client, namespace, name, v))
                    .collect(Collectors.toCollection(ArrayList::new));
            List<Container> initContainers = new ArrayList<>();
            applyOpenClawK8sBootstrap(client, namespace, spec, name, volumes, initContainers);
            var containerBuilder = new io.fabric8.kubernetes.api.model.ContainerBuilder()
                    .withName(name)
                    .withImage(spec.fullImage())
                    .withPorts(containerPorts)
                    .withEnv(envVars)
                    .withVolumeMounts(volumeMounts);
            applyOpenClawStartupArgs(containerBuilder, spec);
            applyResourceLimits(containerBuilder, spec.resources());
            var podSpecBuilder = new PodSpecBuilder()
                    .withContainers(containerBuilder.build())
                    .withVolumes(volumes);
            if (!initContainers.isEmpty()) {
                podSpecBuilder.withInitContainers(initContainers);
            }
            Deployment deployment = new DeploymentBuilder()
                    .withNewMetadata().withName(name).withNamespace(namespace)
                    .addToLabels("app", name)
                    .addToLabels("managed-by", "agent-panel")
                    .endMetadata()
                    .withNewSpec()
                    .withReplicas(spec.replicas() > 0 ? spec.replicas() : 1)
                    .withNewSelector().addToMatchLabels("app", name).endSelector()
                    .withNewTemplate()
                    .withNewMetadata().addToLabels("app", name).endMetadata()
                    .withSpec(podSpecBuilder.build())
                    .endTemplate()
                    .endSpec()
                    .build();
            client.resource(deployment).inNamespace(namespace).serverSideApply();
            boolean needsNodePort = spec.ports().stream().anyMatch(PortMapping::expose);
            List<ServicePort> servicePorts = new ArrayList<>();
            for (PortMapping port : spec.ports()) {
                ServicePortBuilder portBuilder = new ServicePortBuilder()
                        .withName(port.name())
                        .withPort(port.containerPort())
                        .withTargetPort(new IntOrString(port.containerPort()));
                servicePorts.add(portBuilder.build());
            }
            var serviceSpecBuilder = new ServiceSpecBuilder()
                    .addToSelector("app", name)
                    .withPorts(servicePorts);
            if (needsNodePort) {
                serviceSpecBuilder.withType("NodePort");
            }
            io.fabric8.kubernetes.api.model.Service k8sService = new ServiceBuilder()
                    .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                    .withSpec(serviceSpecBuilder.build())
                    .build();
            client.resource(k8sService).inNamespace(namespace).serverSideApply();
            if (spec.exposeViaIngress() || properties.getK8s().isExposeViaIngress()) {
                ensureIngress(client, namespace, name, spec.ports());
            }
            List<PortMapping> resolvedPorts = resolveNodePorts(client, namespace, name, spec.ports());
            log.info("K8s 部署成功: name={} namespace={} image={}", name, namespace, spec.fullImage());
            return new DeployResult(new RuntimeRef(type(), name, namespace), "running", "Deployment 已创建", resolvedPorts);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("K8s 部署失败: name={} namespace={} image={}", name, namespace, spec.fullImage(), e);
            throw new BusinessException("K8s 部署失败: " + e.getMessage());
        }
    }

    @Override
    public void start(RuntimeRef ref) {
        try {
            scale(ref, 1);
            log.info("K8s Deployment 已启动: name={} namespace={}", ref.ref(), ref.namespace());
        } catch (Exception e) {
            log.error("K8s 启动失败: name={} namespace={}", ref.ref(), ref.namespace(), e);
            throw new BusinessException("K8s 启动失败: " + e.getMessage());
        }
    }

    @Override
    public void stop(RuntimeRef ref) {
        try {
            scale(ref, 0);
            log.info("K8s Deployment 已停止: name={} namespace={}", ref.ref(), ref.namespace());
        } catch (Exception e) {
            log.error("K8s 停止失败: name={} namespace={}", ref.ref(), ref.namespace(), e);
            throw new BusinessException("K8s 停止失败: " + e.getMessage());
        }
    }

    @Override
    public void restart(RuntimeRef ref) {
        try (KubernetesClient client = client()) {
            client.apps().deployments().inNamespace(ref.namespace()).withName(ref.ref()).rolling().restart();
            log.info("K8s Deployment 已重启: name={} namespace={}", ref.ref(), ref.namespace());
        } catch (Exception e) {
            log.error("K8s 重启失败: name={} namespace={}", ref.ref(), ref.namespace(), e);
            throw new BusinessException("K8s 重启失败: " + e.getMessage());
        }
    }

    @Override
    public void remove(RuntimeRef ref) {
        try (KubernetesClient client = client()) {
            client.apps().deployments().inNamespace(ref.namespace()).withName(ref.ref()).delete();
            client.services().inNamespace(ref.namespace()).withName(ref.ref()).delete();
            client.secrets().inNamespace(ref.namespace()).withName(ref.ref() + "-env").delete();
            try {
                client.network().v1().ingresses().inNamespace(ref.namespace()).withName(ref.ref()).delete();
            } catch (Exception ignored) {
            }
            log.info("K8s 资源已删除: name={} namespace={}", ref.ref(), ref.namespace());
        } catch (Exception e) {
            log.error("K8s 删除失败: name={} namespace={}", ref.ref(), ref.namespace(), e);
            throw new BusinessException("K8s 删除失败: " + e.getMessage());
        }
    }

    @Override
    public RuntimeStatus status(RuntimeRef ref) {
        try (KubernetesClient client = client()) {
            Deployment deployment = client.apps().deployments().inNamespace(ref.namespace()).withName(ref.ref()).get();
            if (deployment == null) {
                return new RuntimeStatus(RuntimeStatus.Phase.MISSING, false,
                        DockerRuntimeProvider.CONTAINER_MISSING_MARKER);
            }
            Integer ready = deployment.getStatus() != null ? deployment.getStatus().getReadyReplicas() : 0;
            Integer replicas = deployment.getSpec().getReplicas();
            boolean healthy = ready != null && ready > 0;
            RuntimeStatus.Phase phase = healthy ? RuntimeStatus.Phase.RUNNING : RuntimeStatus.Phase.STOPPED;
            return new RuntimeStatus(phase, healthy, "ready=" + ready + "/" + replicas);
        } catch (Exception e) {
            return new RuntimeStatus(RuntimeStatus.Phase.ERROR, false, e.getMessage());
        }
    }

    @Override
    public ResourceStats stats(RuntimeRef ref) {
        try (KubernetesClient client = client()) {
            var pods = client.pods().inNamespace(ref.namespace()).withLabel("app", ref.ref()).list();
            if (pods.getItems().isEmpty()) {
                return ResourceStats.unavailable("未找到运行中的 Pod");
            }
            Pod pod = pods.getItems().get(0);
            String podName = pod.getMetadata().getName();
            long memLimitBytes = 0;
            long cpuLimitNano = 0;
            if (pod.getSpec() != null && pod.getSpec().getContainers() != null
                    && !pod.getSpec().getContainers().isEmpty()) {
                var resources = pod.getSpec().getContainers().get(0).getResources();
                if (resources != null && resources.getLimits() != null) {
                    if (resources.getLimits().get("memory") != null) {
                        memLimitBytes = parseQuantity(resources.getLimits().get("memory"));
                    }
                    if (resources.getLimits().get("cpu") != null) {
                        cpuLimitNano = parseCpuLimitNano(resources.getLimits().get("cpu"));
                    }
                }
            }
            io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList metrics;
            try {
                metrics = client.top().pods().metrics(ref.namespace());
            } catch (Exception e) {
                return ResourceStats.unavailable(classifyMetricsError(e));
            }
            if (metrics == null || metrics.getItems() == null || metrics.getItems().isEmpty()) {
                return ResourceStats.unavailable("metrics-server 未安装或未就绪，请安装 metrics-server");
            }
            for (var item : metrics.getItems()) {
                if (podName.equals(item.getMetadata().getName()) && !item.getContainers().isEmpty()) {
                    var usage = item.getContainers().get(0).getUsage();
                    long cpuNano = parseQuantity(usage.get("cpu"));
                    long memBytes = parseQuantity(usage.get("memory"));
                    double cpuPercent = cpuLimitNano > 0
                            ? (double) cpuNano / cpuLimitNano * 100.0
                            : cpuNano / 10_000_000.0;
                    var network = kubeletStatsHelper.fetchPodNetwork(client, pod);
                    return ResourceStats.ok(cpuPercent, memBytes, memLimitBytes,
                            network.rxBytes(), network.txBytes(), network.available());
                }
            }
            return ResourceStats.unavailable("未找到 Pod 指标数据");
        } catch (Exception e) {
            log.warn("K8s stats 采集失败 deployment={} namespace={}: {}", ref.ref(), ref.namespace(), e.getMessage());
            return ResourceStats.unavailable("K8s stats 采集失败: " + classifyMetricsError(e));
        }
    }

    private String classifyMetricsError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (msg.contains("403") || msg.contains("Forbidden")) {
            return "metrics-server RBAC 不足，请为 agent-panel SA 授权 metrics.k8s.io";
        }
        if (msg.contains("404") || msg.contains("Connection refused") || msg.contains("no metrics")) {
            return "metrics-server 未安装或未就绪，请安装 metrics-server";
        }
        return msg;
    }

    private long parseCpuLimitNano(io.fabric8.kubernetes.api.model.Quantity quantity) {
        if (quantity == null || quantity.getAmount() == null) {
            return 0;
        }
        String amount = quantity.getAmount();
        String format = quantity.getFormat();
        try {
            if ("n".equals(format)) {
                return Long.parseLong(amount);
            }
            if (amount.endsWith("m")) {
                return Long.parseLong(amount.substring(0, amount.length() - 1)) * 1_000_000L;
            }
            double cores = Double.parseDouble(amount);
            return (long) (cores * 1_000_000_000L);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public TerminalSession openTerminal(RuntimeRef ref, TerminalOutputHandler handler) {
        KubernetesClient client = client();
        try {
            var pods = client.pods().inNamespace(ref.namespace()).withLabel("app", ref.ref()).list();
            if (pods.getItems().isEmpty()) {
                throw new BusinessException("未找到运行中的 Pod");
            }
            String podName = pods.getItems().get(0).getMetadata().getName();
            var execWatch = client.pods()
                    .inNamespace(ref.namespace())
                    .withName(podName)
                    .redirectingInput()
                    .withTTY()
                    .exec("sh", "-i");
            return new K8sTerminalSession(client, execWatch, handler);
        } catch (BusinessException e) {
            client.close();
            throw e;
        } catch (Exception e) {
            client.close();
            throw new BusinessException("打开 K8s 终端失败: " + e.getMessage());
        }
    }

    private long parseQuantity(io.fabric8.kubernetes.api.model.Quantity quantity) {
        if (quantity == null || quantity.getAmount() == null) {
            return 0;
        }
        String amount = quantity.getAmount();
        String format = quantity.getFormat();
        try {
            if ("n".equals(format)) {
                return Long.parseLong(amount);
            }
            if (amount.endsWith("m")) {
                return Long.parseLong(amount.substring(0, amount.length() - 1)) * 1_000_000L;
            }
            if (amount.endsWith("Ki")) {
                return Long.parseLong(amount.substring(0, amount.length() - 2)) * 1024L;
            }
            if (amount.endsWith("Mi")) {
                return Long.parseLong(amount.substring(0, amount.length() - 2)) * 1024L * 1024L;
            }
            return Long.parseLong(amount);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Flux<LogLine> logs(RuntimeRef ref, LogOptions options) {
        return Flux.create(sink -> {
            try (KubernetesClient client = client()) {
                var pods = client.pods().inNamespace(ref.namespace()).withLabel("app", ref.ref()).list();
                if (pods.getItems().isEmpty()) {
                    sink.complete();
                    return;
                }
                String podName = pods.getItems().get(0).getMetadata().getName();
                var podOp = client.pods().inNamespace(ref.namespace()).withName(podName);
                LogWatch watch;
                if (options.since() != null && !options.since().isBlank()) {
                    watch = podOp.sinceTime(formatSinceTime(options.since())).watchLog();
                } else {
                    watch = podOp.watchLog();
                }
                try (watch;
                     BufferedReader reader = new BufferedReader(new InputStreamReader(watch.getOutput()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sink.next(new LogLine(line, System.currentTimeMillis()));
                        if (!options.follow()) {
                            break;
                        }
                    }
                    sink.complete();
                }
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    private String formatSinceTime(String since) {
        try {
            if (since.matches("\\d+")) {
                long epoch = Long.parseLong(since);
                if (epoch > 1_000_000_000_000L) {
                    return java.time.Instant.ofEpochMilli(epoch).toString();
                }
                return java.time.Instant.ofEpochSecond(epoch).toString();
            }
            return java.time.Instant.parse(since).toString();
        } catch (Exception e) {
            return since;
        }
    }

    private void scale(RuntimeRef ref, int replicas) {
        try (KubernetesClient client = client()) {
            client.apps().deployments().inNamespace(ref.namespace()).withName(ref.ref()).scale(replicas);
        }
    }

    private Volume buildVolume(KubernetesClient client, String namespace, String deployName, com.agentpanel.runtime.api.VolumeMount mount) {
        String claimName = deployName + "-" + mount.name();
        ensurePvc(client, namespace, claimName);
        return new VolumeBuilder()
                .withName(mount.name())
                .withNewPersistentVolumeClaim().withClaimName(claimName).endPersistentVolumeClaim()
                .build();
    }

    private void ensurePvc(KubernetesClient client, String namespace, String claimName) {
        PersistentVolumeClaim existing = client.persistentVolumeClaims()
                .inNamespace(namespace).withName(claimName).get();
        if (existing != null) {
            return;
        }
        var builder = new PersistentVolumeClaimBuilder()
                .withNewMetadata().withName(claimName).withNamespace(namespace).endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                .addToRequests("storage", new Quantity(properties.getK8s().getVolumeStorageSize()))
                .endResources()
                .endSpec();
        if (properties.getK8s().getStorageClass() != null && !properties.getK8s().getStorageClass().isBlank()) {
            builder.editSpec().withStorageClassName(properties.getK8s().getStorageClass()).endSpec();
        }
        client.persistentVolumeClaims().inNamespace(namespace).resource(builder.build()).create();
    }

    private void ensureNamespace(KubernetesClient client, String namespace) {
        if (client.namespaces().withName(namespace).get() == null) {
            client.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build()).create();
        }
    }

    private void applyResourceLimits(io.fabric8.kubernetes.api.model.ContainerBuilder container, ResourceLimits limits) {
        if (limits == null) {
            return;
        }
        String cpu = limits.cpu();
        String memory = limits.memory();
        if ((cpu == null || cpu.isBlank()) && (memory == null || memory.isBlank())) {
            return;
        }
        var resources = new ResourceRequirementsBuilder();
        var limitsMap = new HashMap<String, Quantity>();
        if (cpu != null && !cpu.isBlank()) {
            limitsMap.put("cpu", new Quantity(formatCpu(cpu)));
        }
        if (memory != null && !memory.isBlank()) {
            limitsMap.put("memory", new Quantity(memory));
        }
        resources.addToLimits(limitsMap);
        container.withResources(resources.build());
    }

    private String formatCpu(String cpu) {
        try {
            double value = Double.parseDouble(cpu.trim());
            if (value < 1) {
                return ((long) (value * 1000)) + "m";
            }
            return cpu.trim();
        } catch (NumberFormatException e) {
            return cpu;
        }
    }

    private void ensureIngress(KubernetesClient client, String namespace, String name, List<PortMapping> ports) {
        List<PortMapping> exposed = ports.stream().filter(PortMapping::expose).toList();
        if (exposed.isEmpty()) {
            return;
        }
        String host = name + "." + properties.getK8s().getIngressHost();
        var paths = new ArrayList<io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath>();
        for (PortMapping port : exposed) {
            paths.add(new io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder()
                    .withPath("/")
                    .withPathType("Prefix")
                    .withNewBackend()
                    .withNewService()
                    .withName(name)
                    .withNewPort().withNumber(port.containerPort()).endPort()
                    .endService()
                    .endBackend()
                    .build());
        }
        var ruleBuilder = new io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder()
                .withHost(host)
                .withNewHttp()
                .withPaths(paths)
                .endHttp();
        var specBuilder = new io.fabric8.kubernetes.api.model.networking.v1.IngressSpecBuilder()
                .withRules(ruleBuilder.build());
        String tlsSecret = properties.getK8s().getIngressTlsSecret();
        if (tlsSecret != null && !tlsSecret.isBlank()) {
            specBuilder.withTls(new io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder()
                    .withHosts(host)
                    .withSecretName(tlsSecret)
                    .build());
        }
        var ingress = new io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .addToAnnotations("kubernetes.io/ingress.class", properties.getK8s().getIngressClass())
                .endMetadata()
                .withSpec(specBuilder.build())
                .build();
        client.network().v1().ingresses().inNamespace(namespace).resource(ingress).serverSideApply();
    }

    private List<PortMapping> resolveNodePorts(KubernetesClient client, String namespace, String name,
                                                List<PortMapping> specPorts) {
        io.fabric8.kubernetes.api.model.Service service = client.services()
                .inNamespace(namespace).withName(name).get();
        if (service == null || service.getSpec() == null || service.getSpec().getPorts() == null) {
            return List.of();
        }
        Map<String, Integer> nodePortsByName = new HashMap<>();
        for (ServicePort sp : service.getSpec().getPorts()) {
            if (sp.getName() != null && sp.getNodePort() != null) {
                nodePortsByName.put(sp.getName(), sp.getNodePort());
            }
        }
        List<PortMapping> resolved = new ArrayList<>();
        for (PortMapping port : specPorts) {
            Integer nodePort = port.expose() ? nodePortsByName.get(port.name()) : null;
            resolved.add(new PortMapping(port.name(), port.containerPort(), nodePort, port.protocol(), port.expose()));
        }
        return resolved;
    }

    private boolean isOpenClaw(DeploySpec spec) {
        if (spec.labels() == null) {
            return false;
        }
        return OpenClawGatewayBootstrapService.OPENCLAW_TEMPLATE_CODE
                .equals(spec.labels().get("agentpanel.template"));
    }

    private void applyOpenClawStartupArgs(io.fabric8.kubernetes.api.model.ContainerBuilder containerBuilder, DeploySpec spec) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (spec.env() != null) {
            merged.putAll(spec.env());
        }
        if (spec.secretEnv() != null) {
            merged.putAll(spec.secretEnv());
        }
        boolean allowUnconfigured = "true".equalsIgnoreCase(merged.get("OPENCLAW_ALLOW_UNCONFIGURED"))
                || "local".equalsIgnoreCase(merged.get("OPENCLAW_GATEWAY_MODE"));
        if (!isOpenClaw(spec) || !allowUnconfigured) {
            return;
        }
        containerBuilder.withCommand("node", "openclaw.mjs", "gateway", "--allow-unconfigured",
                "--bind", "lan", "--auth", "trusted-proxy");
    }

    private void applyOpenClawK8sBootstrap(KubernetesClient client, String namespace, DeploySpec spec, String name,
                                           List<Volume> volumes, List<Container> initContainers) {
        if (!isOpenClaw(spec) || spec.volumes() == null || spec.volumes().isEmpty()) {
            return;
        }
        com.agentpanel.runtime.api.VolumeMount dataVolume = spec.volumes().getFirst();
        String configMapName = openClawGatewayBootstrapService.k8sConfigMapName(name);
        String bootstrapJson = openClawGatewayBootstrapService.buildBootstrapJson("k8s");
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata().withName(configMapName).withNamespace(namespace).endMetadata()
                .addToData(OpenClawGatewayBootstrapService.OPENCLAW_CONFIG_FILE, bootstrapJson)
                .build();
        client.resource(configMap).inNamespace(namespace).serverSideApply();

        volumes.add(new VolumeBuilder()
                .withName("openclaw-bootstrap")
                .withNewConfigMap().withName(configMapName).endConfigMap()
                .build());

        String mountPath = dataVolume.containerPath();
        String configPath = mountPath.endsWith("/")
                ? mountPath + OpenClawGatewayBootstrapService.OPENCLAW_CONFIG_FILE
                : mountPath + "/" + OpenClawGatewayBootstrapService.OPENCLAW_CONFIG_FILE;
        String mergeScript = """
                node -e "const fs=require('fs');const path='%s';const boot=JSON.parse(fs.readFileSync('/bootstrap/%s','utf8'));let cur={};try{if(fs.existsSync(path))cur=JSON.parse(fs.readFileSync(path,'utf8'));}catch(e){}function merge(t,p){for(const[k,v]of Object.entries(p)){if(v&&typeof v==='object'&&!Array.isArray(v)&&t[k]&&typeof t[k]==='object')merge(t[k],v);else t[k]=v;}}merge(cur,boot);fs.mkdirSync('%s',{recursive:true});fs.writeFileSync(path,JSON.stringify(cur,null,2));"
                """.formatted(
                configPath.replace("'", "\\'"),
                OpenClawGatewayBootstrapService.OPENCLAW_CONFIG_FILE,
                mountPath.replace("'", "\\'"));

        initContainers.add(new ContainerBuilder()
                .withName("openclaw-bootstrap")
                .withImage(spec.fullImage())
                .withCommand("sh", "-c", mergeScript)
                .withVolumeMounts(
                        new VolumeMountBuilder().withName(dataVolume.name()).withMountPath(mountPath).build(),
                        new VolumeMountBuilder().withName("openclaw-bootstrap").withMountPath("/bootstrap").build())
                .build());
    }
}
