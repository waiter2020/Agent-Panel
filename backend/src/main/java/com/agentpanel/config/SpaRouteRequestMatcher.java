package com.agentpanel.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Matches SPA frontend routes: GET requests without file extension, excluding API paths.
 */
public class SpaRouteRequestMatcher implements RequestMatcher {

  private static final String[] EXCLUDED_PREFIXES = {"/api", "/v1", "/actuator"};

  @Override
  public boolean matches(HttpServletRequest request) {
    if (!HttpMethod.GET.matches(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    if (path == null || path.isEmpty()) {
      return false;
    }
    for (String prefix : EXCLUDED_PREFIXES) {
      if (path.startsWith(prefix)) {
        return false;
      }
    }
    // Exclude static files (contain a dot in the last path segment)
    int lastSlash = path.lastIndexOf('/');
    String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    return !lastSegment.contains(".");
  }
}
