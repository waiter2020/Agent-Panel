package com.agentpanel.application;

import com.agentpanel.application.service.AppWebConsoleProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class AppWebConsoleProxyController {

    private final AppWebConsoleProxyService proxyService;

    @RequestMapping(value = "/api/apps/{appId}/proxy/{portRef}/**", headers = "!Upgrade")
    @PreAuthorize("hasAuthority('app:read')")
    public void proxy(@PathVariable Long appId,
                      @PathVariable String portRef,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        proxyService.proxy(appId, portRef, request, response);
    }
}
