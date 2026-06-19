package com.huatai.careeragent.auth;

import com.huatai.careeragent.auth.AuthDtos.LoginRequest;
import com.huatai.careeragent.auth.AuthDtos.LoginResponse;
import com.huatai.careeragent.auth.AuthDtos.RegisterRequest;
import com.huatai.careeragent.auth.AuthDtos.UserResponse;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.common.security.JwtService;
import com.huatai.careeragent.user.User;
import com.huatai.careeragent.user.UserRepository;
import com.huatai.careeragent.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("EMAIL_ALREADY_REGISTERED", "Email already registered", HttpStatus.CONFLICT);
        }

        String nickname = StringUtils.hasText(request.nickname()) ? request.nickname().trim() : null;
        User user = new User(email, passwordEncoder.encode(request.password()), nickname, UserRole.USER);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        String token = jwtService.createToken(user);
        return new LoginResponse(token, "Bearer", jwtService.expiresInSeconds(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser currentUser) {
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        return UserResponse.from(user);
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
