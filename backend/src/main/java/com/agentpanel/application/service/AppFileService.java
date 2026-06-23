package com.agentpanel.application.service;

import com.agentpanel.common.BusinessException;
import com.agentpanel.runtime.docker.DockerVolumePermissionService;
import com.agentpanel.runtime.k8s.K8sVolumeFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AppFileService {

    private final ApplicationService applicationService;
    private final K8sVolumeFileService k8sVolumeFileService;
    private final DockerVolumePermissionService volumePermissionService;

    public List<FileEntry> list(Long appId, String volume, String relativePath) {
        if (isK8s(appId)) {
            return k8sVolumeFileService.list(
                    applicationService.runtimeRef(appId),
                    applicationService.resolveVolumeContainerPath(appId, volume),
                    relativePath
            );
        }
        Path base = resolveSafeHostPath(appId, volume, relativePath);
        if (!Files.isDirectory(base)) {
            throw new BusinessException("目录不存在");
        }
        try (Stream<Path> stream = Files.list(base)) {
            return stream.map(this::toEntry).sorted(Comparator.comparing(FileEntry::name)).toList();
        } catch (IOException e) {
            throw new BusinessException("读取目录失败: " + e.getMessage());
        }
    }

    public void upload(Long appId, String volume, String relativePath, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException("文件名无效");
        }
        if (isK8s(appId)) {
            k8sVolumeFileService.upload(
                    applicationService.runtimeRef(appId),
                    applicationService.resolveVolumeContainerPath(appId, volume),
                    relativePath,
                    file
            );
            return;
        }
        String fullPath = relativePath == null || relativePath.isBlank()
                ? fileName
                : (relativePath.endsWith("/") ? relativePath + fileName : relativePath + "/" + fileName);
        Path target = resolveSafeHostPath(appId, volume, fullPath);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            volumePermissionService.prepareVolumeDirectory(applicationService.resolveVolumeHostPath(appId, volume));
        } catch (IOException e) {
            throw new BusinessException("上传失败: " + e.getMessage());
        }
    }

    public byte[] download(Long appId, String volume, String relativePath) {
        if (isK8s(appId)) {
            return k8sVolumeFileService.download(
                    applicationService.runtimeRef(appId),
                    applicationService.resolveVolumeContainerPath(appId, volume),
                    relativePath
            );
        }
        Path target = resolveSafeHostPath(appId, volume, relativePath);
        if (!Files.exists(target)) {
            throw new BusinessException("文件不存在");
        }
        if (Files.isDirectory(target)) {
            throw new BusinessException("不能下载目录");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BusinessException("读取文件失败: " + e.getMessage());
        }
    }

    public String readText(Long appId, String volume, String relativePath) {
        byte[] content = download(appId, volume, relativePath);
        return new String(content, java.nio.charset.StandardCharsets.UTF_8);
    }

    public void writeText(Long appId, String volume, String relativePath, String content) {
        if (isK8s(appId)) {
            k8sVolumeFileService.writeText(
                    applicationService.runtimeRef(appId),
                    applicationService.resolveVolumeContainerPath(appId, volume),
                    relativePath,
                    content
            );
            return;
        }
        Path target = resolveSafeHostPath(appId, volume, relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            volumePermissionService.prepareVolumeDirectory(applicationService.resolveVolumeHostPath(appId, volume));
        } catch (IOException e) {
            throw new BusinessException("写入失败: " + e.getMessage());
        }
    }

    public void mkdir(Long appId, String volume, String relativePath) {
        if (isK8s(appId)) {
            k8sVolumeFileService.mkdir(
                    applicationService.runtimeRef(appId),
                    applicationService.resolveVolumeContainerPath(appId, volume),
                    relativePath
            );
            return;
        }
        Path target = resolveSafeHostPath(appId, volume, relativePath);
        try {
            Files.createDirectories(target);
            volumePermissionService.prepareVolumeDirectory(applicationService.resolveVolumeHostPath(appId, volume));
        } catch (IOException e) {
            throw new BusinessException("创建目录失败: " + e.getMessage());
        }
    }

    public void rename(Long appId, String volume, String relativePath, String newName) {
        if (newName == null || newName.isBlank() || newName.contains("/") || newName.contains("\\")) {
            throw new BusinessException("新名称无效");
        }
        if (isK8s(appId)) {
            k8sVolumeFileService.rename(
                    applicationService.runtimeRef(appId),
                    applicationService.resolveVolumeContainerPath(appId, volume),
                    relativePath,
                    newName
            );
            return;
        }
        Path source = resolveSafeHostPath(appId, volume, relativePath);
        if (!Files.exists(source)) {
            throw new BusinessException("文件不存在");
        }
        Path target = source.getParent().resolve(newName).normalize().toAbsolutePath();
        Path base = applicationService.resolveVolumeHostPath(appId, volume).normalize().toAbsolutePath();
        if (!target.startsWith(base)) {
            throw new BusinessException("非法路径");
        }
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("重命名失败: " + e.getMessage());
        }
    }

    public void delete(Long appId, String volume, String relativePath) {
        if (isK8s(appId)) {
            k8sVolumeFileService.delete(
                    applicationService.runtimeRef(appId),
                    applicationService.resolveVolumeContainerPath(appId, volume),
                    relativePath
            );
            return;
        }
        Path target = resolveSafeHostPath(appId, volume, relativePath);
        if (!Files.exists(target)) {
            throw new BusinessException("文件不存在");
        }
        try {
            if (Files.isDirectory(target)) {
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new BusinessException("删除失败: " + e.getMessage());
                        }
                    });
                }
            } else {
                Files.delete(target);
            }
        } catch (IOException e) {
            throw new BusinessException("删除失败: " + e.getMessage());
        }
    }

    private boolean isK8s(Long appId) {
        return "k8s".equals(applicationService.resolveProviderForApp(appId));
    }

    private Path resolveSafeHostPath(Long appId, String volume, String relativePath) {
        applicationService.get(appId);
        Path base = applicationService.resolveVolumeHostPath(appId, volume).normalize().toAbsolutePath();
        String safeRelative = relativePath == null || relativePath.isBlank() ? "" : relativePath;
        Path resolved = base.resolve(safeRelative).normalize().toAbsolutePath();
        if (!resolved.startsWith(base)) {
            throw new BusinessException("非法路径");
        }
        return resolved;
    }

    private FileEntry toEntry(Path path) {
        try {
            return new FileEntry(
                    path.getFileName().toString(),
                    Files.isDirectory(path),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toMillis()
            );
        } catch (IOException e) {
            return new FileEntry(path.getFileName().toString(), Files.isDirectory(path), 0, 0);
        }
    }

    public record FileEntry(String name, boolean directory, long size, long modifiedAt) {
    }
}
