package com.agentpanel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "agent.runtime")
public class AgentRuntimeProperties {

    private String provider = "docker";
    private boolean terminalEnabled = true;
    private Docker docker = new Docker();
    private K8s k8s = new K8s();

    @Getter
    @Setter
    public static class Docker {
        private String host = "unix:///var/run/docker.sock";
        private String dataRoot = "/data/apps";
        /** Docker 宿主机上的数据根路径；未设置时从 panel 容器 mount 自动探测 */
        private String hostDataRoot = "";
        private String network = "agentpanel-net";
        private String accessHost = "localhost";
        private Duration responseTimeout = Duration.ofSeconds(300);
        /** 应用容器内写卷数据的默认用户/组（OpenClaw 等 Node 镜像通常为 1000） */
        /** 面板数据 named volume 名称（可选，未设置时从挂载路径自动解析） */
        private String dataVolumeName = "";
        private int volumeUid = 1000;
        private int volumeGid = 1000;
    }

    @Getter
    @Setter
    public static class K8s {
        private String namespace = "agentpanel-apps";
        private boolean inCluster = false;
        private String storageClass = "";
        private String volumeStorageSize = "1Gi";
        private String accessHost = "localhost";
        private boolean exposeViaIngress = false;
        private String ingressHost = "agentpanel.local";
        private String ingressClass = "nginx";
        private String ingressTlsSecret = "";
    }
}
