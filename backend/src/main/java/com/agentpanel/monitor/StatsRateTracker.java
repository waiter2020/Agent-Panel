package com.agentpanel.monitor;

import com.agentpanel.runtime.api.ResourceStats;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
class StatsRateTracker {

    private final ConcurrentHashMap<Long, Sample> samples = new ConcurrentHashMap<>();

    ResourceStats enrich(Long appId, ResourceStats stats) {
        if (!stats.available()) {
            samples.remove(appId);
            return stats;
        }
        Sample prev = samples.get(appId);
        long now = System.currentTimeMillis();
        if (prev == null) {
            samples.put(appId, new Sample(now, stats.netRxBytes(), stats.netTxBytes()));
            return stats.withRates(0, 0);
        }
        double deltaSec = (now - prev.timestampMs()) / 1000.0;
        if (deltaSec <= 0) {
            return stats.withRates(0, 0);
        }
        double rxRate = Math.max(0, (stats.netRxBytes() - prev.netRxBytes()) / deltaSec);
        double txRate = Math.max(0, (stats.netTxBytes() - prev.netTxBytes()) / deltaSec);
        samples.put(appId, new Sample(now, stats.netRxBytes(), stats.netTxBytes()));
        return stats.withRates(rxRate, txRate);
    }

    void clear(Long appId) {
        samples.remove(appId);
    }

    private record Sample(long timestampMs, long netRxBytes, long netTxBytes) {
    }
}
