package com.agentpanel.system;

import com.agentpanel.common.ApiResponse;
import com.agentpanel.common.PageResult;
import com.agentpanel.system.dto.UserDto;
import com.agentpanel.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:read')")
    public ApiResponse<PageResult<UserDto>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(userService.list(page, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:read')")
    public ApiResponse<UserDto> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:write')")
    public ApiResponse<UserDto> create(@RequestBody UserDto dto) {
        return ApiResponse.ok(userService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:write')")
    public ApiResponse<UserDto> update(@PathVariable Long id, @RequestBody UserDto dto) {
        return ApiResponse.ok(userService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:write')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('system:user:write')")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("password"));
        return ApiResponse.ok();
    }
}
