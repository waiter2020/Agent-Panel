package com.agentpanel.runtime.api;

public record ResourceStats(
        double cpuPercent,
        long memUsedBytes,
        long memLimitBytes,
        long netRxBytes,
        long netTxBytes,
        double netRxBytesPerSec,
        double netTxBytesPerSec,
        boolean networkAvailable,
        boolean available,
        String message
) {
    public static ResourceStats unavailable(String message) {
        return new ResourceStats(0, 0, 0, 0, 0, 0, 0, false, false, message);
    }

    public static ResourceStats ok(double cpuPercent, long memUsedBytes, long memLimitBytes,
                                   long netRxBytes, long netTxBytes, boolean networkAvailable) {
        return new ResourceStats(cpuPercent, memUsedBytes, memLimitBytes, netRxBytes, netTxBytes,
                0, 0, networkAvailable, true, null);
    }

    public ResourceStats withRates(double netRxBytesPerSec, double netTxBytesPerSec) {
        return new ResourceStats(cpuPercent, memUsedBytes, memLimitBytes, netRxBytes, netTxBytes,
                netRxBytesPerSec, netTxBytesPerSec, networkAvailable, available, message);
    }
}
