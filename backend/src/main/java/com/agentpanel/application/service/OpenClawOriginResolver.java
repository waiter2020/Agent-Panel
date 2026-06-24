package com.agentpanel.application.service;

import com.agentpanel.config.OpenClawProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenClawOriginResolver {

    private final OpenClawProperties openClawProperties;

    public String resolveForBrowserSession(WebSocketSession browserSession) {
        if (browserSession == null) {
            return fallbackOrigin();
        }
        HttpHeaders headers = browserSession.getHandshakeHeaders();
        String origin = firstHeader(headers, "Origin");
        if (isUsableOrigin(origin)) {
            return origin.trim();
        }
        String referer = firstHeader(headers, "Referer");
        String fromReferer = extractOriginFromUrl(referer);
        if (isUsableOrigin(fromReferer)) {
            return fromReferer;
        }
        return fallbackOrigin();
    }

    public List<String> resolveAllowedOrigins(HttpServletRequest request) {
        LinkedHashSet<String> origins = new LinkedHashSet<>(openClawProperties.resolvePanelPublicOrigins());
        String requestOrigin = resolveFromHttpRequest(request);
        if (isUsableOrigin(requestOrigin)) {
            origins.add(requestOrigin.trim());
        }
        return new ArrayList<>(origins);
    }

    public String resolveFromHttpRequest(HttpServletRequest request) {
        if (request == null) {
            return fallbackOrigin();
        }
        String origin = request.getHeader("Origin");
        if (isUsableOrigin(origin)) {
            return origin.trim();
        }
        String fromReferer = extractOriginFromUrl(request.getHeader("Referer"));
        if (isUsableOrigin(fromReferer)) {
            return fromReferer;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) {
            proto = request.isSecure() ? "https" : "http";
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host != null && !host.isBlank()) {
            return proto + "://" + host.trim();
        }
        return fallbackOrigin();
    }

    static String extractOriginFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            if (uri.getPort() > 0) {
                return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
            }
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fallbackOrigin() {
        List<String> configured = openClawProperties.resolvePanelPublicOrigins();
        if (configured.isEmpty()) {
            return null;
        }
        return configured.getFirst();
    }

    private static String firstHeader(HttpHeaders headers, String name) {
        if (headers == null) {
            return null;
        }
        return headers.getFirst(name);
    }

    static boolean isUsableOrigin(String origin) {
        return origin != null && !origin.isBlank() && !"null".equalsIgnoreCase(origin.trim());
    }
}
