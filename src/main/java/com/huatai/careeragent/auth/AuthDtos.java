package com.huatai.careeragent.auth;

import com.huatai.careeragent.user.User;
import com.huatai.careeragent.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 100) String nickname
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record UserResponse(
            Long userId,
            String email,
            String nickname,
            UserRole role
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
        }
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserResponse user
    ) {
    }
}
