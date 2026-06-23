package com.agentpanel.application;

import com.agentpanel.application.dto.DashboardStatsDto;
import com.agentpanel.application.dto.PortUsageDto;
import com.agentpanel.application.service.DashboardService;
import com.agentpanel.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/dashboard/stats")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<DashboardStatsDto> stats() {
        return ApiResponse.ok(dashboardService.getStats());
    }

    @GetMapping("/api/apps/ports")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<List<PortUsageDto>> portUsage() {
        return ApiResponse.ok(dashboardService.listPortUsage());
    }
}
