package com.agentpanel.application.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DashboardStatsDto {
    private int totalApps;
    private int runningApps;
    private int stoppedApps;
    private int deployingApps;
    private int errorApps;
    private Map<String, Integer> byTemplate;
    private Map<String, Integer> byRuntime;
    private int exposedPorts;
    private int internalPorts;
    private int portConflicts;
    private int topologyCount;
    private int deployedTopologies;
    private List<Map<String, Object>> portUsage;
    private List<Map<String, Object>> recentApps;
}
