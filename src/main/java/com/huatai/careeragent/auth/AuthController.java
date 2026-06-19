package com.huatai.careeragent.auth;

import com.huatai.careeragent.auth.AuthDtos.LoginRequest;
import com.huatai.careeragent.auth.AuthDtos.LoginResponse;
import com.huatai.careeragent.auth.AuthDtos.RegisterRequest;
import com.huatai.careeragent.auth.AuthDtos.UserResponse;
import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(CurrentUser currentUser) {
        return ApiResponse.ok(authService.me(currentUser));
    }
}
