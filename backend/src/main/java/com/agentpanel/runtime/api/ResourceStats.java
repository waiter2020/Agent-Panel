package com.agentpanel.runtime.api;

public record ResourceStats(
        double cpuPercent,
        long memUsedBytes,
        long memLimitBytes,
        long netRxBytes,
        long netTxBytes,
        boolean available,
        String message
) {
    public static ResourceStats unavailable(String message) {
        return new ResourceStats(0, 0, 0, 0, 0, false, message);
    }

    public static ResourceStats ok(double cpuPercent, long memUsedBytes, long memLimitBytes,
                                   long netRxBytes, long netTxBytes) {
        return new ResourceStats(cpuPercent, memUsedBytes, memLimitBytes, netRxBytes, netTxBytes, true, null);
    }
}
