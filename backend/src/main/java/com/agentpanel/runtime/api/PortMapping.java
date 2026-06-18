package com.agentpanel.runtime.api;

public record PortMapping(String name, int containerPort, Integer hostPort, String protocol, boolean expose) {}
