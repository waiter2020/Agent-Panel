package com.agentpanel.config;

import com.agentpanel.auth.ApiKeyAuthFilter;
import com.agentpanel.auth.JwtAccessDeniedHandler;
import com.agentpanel.auth.JwtAuthFilter;
import com.agentpanel.auth.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;
  private final ApiKeyAuthFilter apiKeyAuthFilter;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/login", "/api/auth/refresh")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasRole("SUPER_ADMIN")
                    .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .requestMatchers("/v1/**")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/index.html",
                        "/umi.js",
                        "/umi.css",
                        "/preload_helper.js",
                        "/favicon.ico",
                        "/favicon.svg",
                        "/logo.svg",
                        "/assets/**",
                        "/**/*.js",
                        "/**/*.css",
                        "/**/*.svg",
                        "/**/*.ico",
                        "/**/*.png",
                        "/**/*.map",
                        "/**/*.woff",
                        "/**/*.woff2")
                    .permitAll()
                    .requestMatchers(new SpaRouteRequestMatcher())
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(apiKeyAuthFilter, JwtAuthFilter.class);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }
}
