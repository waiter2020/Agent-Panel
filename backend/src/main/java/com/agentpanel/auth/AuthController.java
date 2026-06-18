package com.agentpanel.auth;

import com.agentpanel.auth.dto.CurrentUserResponse;
import com.agentpanel.auth.dto.LoginRequest;
import com.agentpanel.auth.dto.LoginResponse;
import com.agentpanel.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(authService.refresh(body.get("refreshToken")));
    }

    @GetMapping("/current")
    public ApiResponse<CurrentUserResponse> current() {
        return ApiResponse.ok(authService.currentUser());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) Map<String, String> body) {
        String refreshToken = body != null ? body.get("refreshToken") : null;
        authService.logout(refreshToken);
        return ApiResponse.ok();
    }

    @PostMapping("/sse-ticket")
    public ApiResponse<Map<String, String>> sseTicket() {
        return ApiResponse.ok(authService.createSseTicket());
    }
}
