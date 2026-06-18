package com.agentpanel.runtime.api;

public record LogOptions(boolean follow, int tail, String since) {}
