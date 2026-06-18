package com.agentpanel.runtime.docker;

/**
 * 面板数据目录在 Docker 中的挂载方式。
 *
 * @param volumeName  named volume 名称（优先用于子路径挂载）
 * @param bindHostRoot bind 模式下的宿主机根路径
 */
public record PanelDataMount(String volumeName, String bindHostRoot) {

    public boolean usesNamedVolume() {
        return volumeName != null && !volumeName.isBlank();
    }

    public String hostRoot() {
        return bindHostRoot != null ? bindHostRoot : "";
    }
}
