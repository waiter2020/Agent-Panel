package com.agentpanel.runtime.k8s;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubeletStatsHelper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public NetworkStats fetchPodNetwork(KubernetesClient client, Pod pod) {
        String nodeName = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;
        String podName = pod.getMetadata() != null ? pod.getMetadata().getName() : null;
        String namespace = pod.getMetadata() != null ? pod.getMetadata().getNamespace() : null;
        if (nodeName == null || podName == null || namespace == null) {
            return NetworkStats.unavailable("Pod 元数据不完整，无法读取网络指标");
        }
        try {
            String summary = client.raw("/api/v1/nodes/" + nodeName + "/proxy/stats/summary");
            JsonNode root = objectMapper.readTree(summary);
            JsonNode pods = root.path("pods");
            if (!pods.isArray()) {
                return NetworkStats.unavailable("kubelet summary 响应格式异常");
            }
            for (JsonNode podStats : pods) {
                JsonNode podRef = podStats.path("podRef");
                if (podName.equals(podRef.path("name").asText())
                        && namespace.equals(podRef.path("namespace").asText())) {
                    JsonNode network = podStats.path("network");
                    long rx = network.path("rxBytes").asLong(0);
                    long tx = network.path("txBytes").asLong(0);
                    return NetworkStats.ok(rx, tx);
                }
            }
            return NetworkStats.unavailable("kubelet summary 中未找到 Pod 网络数据");
        } catch (Exception e) {
            String message = classifyNetworkError(e);
            log.debug("kubelet 网络指标采集失败 pod={} node={}: {}", podName, nodeName, message);
            return NetworkStats.unavailable(message);
        }
    }

    private String classifyNetworkError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (msg.contains("403") || msg.contains("Forbidden")) {
            return "nodes/proxy 权限不足，请为 agent-panel SA 授权 nodes/stats 与 nodes/proxy";
        }
        if (msg.contains("404") || msg.contains("Connection refused")) {
            return "无法访问 kubelet stats/summary: " + msg;
        }
        return "kubelet 网络指标采集失败: " + msg;
    }

    public record NetworkStats(long rxBytes, long txBytes, boolean available, String message) {
        static NetworkStats ok(long rxBytes, long txBytes) {
            return new NetworkStats(rxBytes, txBytes, true, null);
        }

        static NetworkStats unavailable(String message) {
            return new NetworkStats(0, 0, false, message);
        }
    }
}
