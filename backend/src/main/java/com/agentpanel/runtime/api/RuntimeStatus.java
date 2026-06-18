package com.agentpanel.runtime.api;

public record RuntimeStatus(Phase phase, boolean healthy, String message) {
    public enum Phase {
        CREATED, RUNNING, STOPPED, ERROR, UNKNOWN
    }
}
