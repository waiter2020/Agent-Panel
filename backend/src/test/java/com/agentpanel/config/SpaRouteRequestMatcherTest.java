package com.agentpanel.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaRouteRequestMatcherTest {

  private final SpaRouteRequestMatcher matcher = new SpaRouteRequestMatcher();

  @Test
  void matchesSpaRoutes() {
    assertTrue(matcher.matches(request("GET", "/login")));
    assertTrue(matcher.matches(request("GET", "/dashboard")));
    assertTrue(matcher.matches(request("GET", "/app/detail/1")));
  }

  @Test
  void rejectsApiAndStaticPaths() {
    assertFalse(matcher.matches(request("GET", "/api/auth/current")));
    assertFalse(matcher.matches(request("GET", "/v1/chat/completions")));
    assertFalse(matcher.matches(request("GET", "/umi.js")));
    assertFalse(matcher.matches(request("POST", "/login")));
  }

  private MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRequestURI(uri);
    return request;
  }
}
