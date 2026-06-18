package com.agentpanel.config;

import com.agentpanel.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SpaSecurityIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @MockitoBean
  private StorageService storageService;

  @Autowired private MockMvc mockMvc;

  @Test
  void spaLoginRouteReturnsIndexHtml() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("root")));
  }

  @Test
  void spaDashboardRouteReturnsIndexHtml() throws Exception {
    mockMvc.perform(get("/dashboard")).andExpect(status().isOk());
  }

  @Test
  void spaNestedRouteReturnsIndexHtml() throws Exception {
    mockMvc.perform(get("/app/list")).andExpect(status().isOk());
  }

  @Test
  void staticUmiJsIsAccessible() throws Exception {
    mockMvc.perform(get("/umi.js")).andExpect(status().isOk());
  }

  @Test
  void staticAsyncChunkIsAccessible() throws Exception {
    mockMvc.perform(get("/p__login__index.async.js")).andExpect(status().isOk());
  }

  @Test
  void currentUserWithoutTokenReturns401() throws Exception {
    mockMvc
        .perform(get("/api/auth/current"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401));
  }

  @Test
  void loginEndpointIsPublic() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
  }
}
