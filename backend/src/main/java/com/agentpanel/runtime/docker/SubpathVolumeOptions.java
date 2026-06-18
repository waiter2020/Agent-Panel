package com.agentpanel.runtime.docker;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.dockerjava.api.model.VolumeOptions;

/**
 * Docker Engine 支持通过 VolumeOptions.Subpath 挂载卷内子目录；
 * docker-java 上游模型未包含该字段，故在此扩展。
 */
public class SubpathVolumeOptions extends VolumeOptions {

    @JsonProperty("Subpath")
    private String subpath;

    public String getSubpath() {
        return subpath;
    }

    public SubpathVolumeOptions withSubpath(String subpath) {
        this.subpath = subpath;
        return this;
    }
}
