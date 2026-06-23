package com.agentpanel.auth;

import com.agentpanel.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final Pattern PROXY_PATH = Pattern.compile("^/api/apps/\\d+/proxy(?:-ws)?/");

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    log.warn("未认证访问: {} {} - {}", request.getMethod(), request.getRequestURI(), authException.getMessage());
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    if (isProxyHtmlRequest(request)) {
      response.setContentType(MediaType.TEXT_HTML_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write("""
          <!DOCTYPE html><html><head><meta charset="utf-8"><title>代理会话失效</title></head>
          <body style="font-family:sans-serif;padding:24px;color:#333;">
          <h3>Web 控制台代理会话已失效</h3>
          <p>请关闭此 Tab 后重新打开 Gateway 页面，或返回应用详情重新加载。</p>
          </body></html>
          """);
      return;
    }
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(401, "未登录"));
  }

  private boolean isProxyHtmlRequest(HttpServletRequest request) {
    if (!PROXY_PATH.matcher(request.getRequestURI()).find()) {
      return false;
    }
    String accept = request.getHeader("Accept");
    return accept != null && accept.contains("text/html");
  }
}
