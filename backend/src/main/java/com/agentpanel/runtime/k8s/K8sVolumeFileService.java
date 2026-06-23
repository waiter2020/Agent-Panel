package com.agentpanel.runtime.k8s;

import com.agentpanel.application.service.AppFileService;
import com.agentpanel.common.BusinessException;
import com.agentpanel.config.AgentRuntimeProperties;
import com.agentpanel.runtime.api.RuntimeRef;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class K8sVolumeFileService {

    private final AgentRuntimeProperties properties;

    public List<AppFileService.FileEntry> list(RuntimeRef ref, String containerMountPath, String relativePath) {
        String dirPath = joinContainerPath(containerMountPath, relativePath);
        validateContainerPath(dirPath);
        try (KubernetesClient client = client()) {
            PodResource pod = resolveRunningPod(client, ref);
            if (!execTest(pod, "-d", dirPath)) {
                throw new BusinessException("目录不存在");
            }
            String output = execCapture(pod, "sh", "-c",
                    "ls -la --time-style=+%s " + shellQuote(dirPath) + " 2>/dev/null | tail -n +2");
            return parseLsOutput(output);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取目录失败: " + e.getMessage());
        }
    }

    public void upload(RuntimeRef ref, String containerMountPath, String relativePath, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException("文件名无效");
        }
        String fullRelative = relativePath == null || relativePath.isBlank()
                ? fileName
                : (relativePath.endsWith("/") ? relativePath + fileName : relativePath + "/" + fileName);
        String targetPath = joinContainerPath(containerMountPath, fullRelative);
        validateContainerPath(targetPath);
        try (KubernetesClient client = client()) {
            PodResource pod = resolveRunningPod(client, ref);
            String parent = parentPath(targetPath);
            if (!execTest(pod, "-d", parent)) {
                execCapture(pod, "sh", "-c", "mkdir -p " + shellQuote(parent));
            }
            try (InputStream input = file.getInputStream()) {
                pod.file(targetPath).upload(input);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("上传失败: " + e.getMessage());
        }
    }

    public byte[] download(RuntimeRef ref, String containerMountPath, String relativePath) {
        String targetPath = joinContainerPath(containerMountPath, relativePath);
        validateContainerPath(targetPath);
        try (KubernetesClient client = client()) {
            PodResource pod = resolveRunningPod(client, ref);
            if (execTest(pod, "-d", targetPath)) {
                throw new BusinessException("不能下载目录");
            }
            if (!execTest(pod, "-e", targetPath)) {
                throw new BusinessException("文件不存在");
            }
            try (InputStream input = pod.file(targetPath).read()) {
                return input.readAllBytes();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取文件失败: " + e.getMessage());
        }
    }

    public void delete(RuntimeRef ref, String containerMountPath, String relativePath) {
        String targetPath = joinContainerPath(containerMountPath, relativePath);
        validateContainerPath(targetPath);
        try (KubernetesClient client = client()) {
            PodResource pod = resolveRunningPod(client, ref);
            if (!execTest(pod, "-e", targetPath)) {
                throw new BusinessException("文件不存在");
            }
            if (execTest(pod, "-d", targetPath)) {
                execCapture(pod, "sh", "-c", "rm -rf " + shellQuote(targetPath));
            } else {
                execCapture(pod, "sh", "-c", "rm -f " + shellQuote(targetPath));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("删除失败: " + e.getMessage());
        }
    }

    public void writeText(RuntimeRef ref, String containerMountPath, String relativePath, String content) {
        String targetPath = joinContainerPath(containerMountPath, relativePath);
        validateContainerPath(targetPath);
        try (KubernetesClient client = client()) {
            PodResource pod = resolveRunningPod(client, ref);
            String parent = parentPath(targetPath);
            if (!execTest(pod, "-d", parent)) {
                execCapture(pod, "sh", "-c", "mkdir -p " + shellQuote(parent));
            }
            try (InputStream input = new java.io.ByteArrayInputStream(
                    (content == null ? "" : content).getBytes(StandardCharsets.UTF_8))) {
                pod.file(targetPath).upload(input);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("写入失败: " + e.getMessage());
        }
    }

    public void mkdir(RuntimeRef ref, String containerMountPath, String relativePath) {
        String targetPath = joinContainerPath(containerMountPath, relativePath);
        validateContainerPath(targetPath);
        try (KubernetesClient client = client()) {
            PodResource pod = resolveRunningPod(client, ref);
            execCapture(pod, "sh", "-c", "mkdir -p " + shellQuote(targetPath));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("创建目录失败: " + e.getMessage());
        }
    }

    public void rename(RuntimeRef ref, String containerMountPath, String relativePath, String newName) {
        String sourcePath = joinContainerPath(containerMountPath, relativePath);
        validateContainerPath(sourcePath);
        String parent = parentPath(sourcePath);
        String targetPath = parent + "/" + newName;
        validateContainerPath(targetPath);
        try (KubernetesClient client = client()) {
            PodResource pod = resolveRunningPod(client, ref);
            if (!execTest(pod, "-e", sourcePath)) {
                throw new BusinessException("文件不存在");
            }
            execCapture(pod, "sh", "-c", "mv " + shellQuote(sourcePath) + " " + shellQuote(targetPath));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("重命名失败: " + e.getMessage());
        }
    }

    private PodResource resolveRunningPod(KubernetesClient client, RuntimeRef ref) {
        String namespace = ref.namespace() != null ? ref.namespace() : properties.getK8s().getNamespace();
        var pods = client.pods().inNamespace(namespace).withLabel("app", ref.ref()).list();
        if (pods.getItems().isEmpty()) {
            throw new BusinessException("未找到运行中的 Pod，请确认应用已部署且处于运行状态");
        }
        Pod running = pods.getItems().stream()
                .filter(p -> p.getStatus() != null && "Running".equals(p.getStatus().getPhase()))
                .findFirst()
                .orElse(pods.getItems().get(0));
        String podName = running.getMetadata().getName();
        return client.pods().inNamespace(namespace).withName(podName);
    }

    private boolean execTest(PodResource pod, String flag, String path) throws Exception {
        String output = execCapture(pod, "sh", "-c", "test " + flag + " " + shellQuote(path) + " && echo yes || echo no");
        return output.trim().equals("yes");
    }

    private String execCapture(PodResource pod, String... command) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ExecWatch watch = pod.writingOutput(output).exec(command)) {
            if (!watch.exitCode().get(30, TimeUnit.SECONDS).equals(0)) {
                String err = output.toString(StandardCharsets.UTF_8);
                throw new BusinessException("Pod 命令执行失败: " + err);
            }
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private List<AppFileService.FileEntry> parseLsOutput(String output) {
        List<AppFileService.FileEntry> entries = new ArrayList<>();
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 7) {
                continue;
            }
            String name = parts[parts.length - 1];
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            boolean directory = trimmed.startsWith("d");
            long size = directory ? 0 : parseLong(parts[4]);
            long modifiedAt = parseLong(parts[5]) * 1000L;
            entries.add(new AppFileService.FileEntry(name, directory, size, modifiedAt));
        }
        entries.sort(Comparator.comparing(AppFileService.FileEntry::name));
        return entries;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String joinContainerPath(String mountPath, String relativePath) {
        String base = mountPath.endsWith("/") ? mountPath.substring(0, mountPath.length() - 1) : mountPath;
        if (relativePath == null || relativePath.isBlank()) {
            return base;
        }
        String rel = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        if (rel.contains("..")) {
            throw new BusinessException("非法路径");
        }
        return base + "/" + rel;
    }

    private void validateContainerPath(String path) {
        if (path.contains("..") || path.contains("\0")) {
            throw new BusinessException("非法路径");
        }
    }

    private String parentPath(String path) {
        int idx = path.lastIndexOf('/');
        return idx > 0 ? path.substring(0, idx) : "/";
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private KubernetesClient client() {
        return new KubernetesClientBuilder().build();
    }
}
