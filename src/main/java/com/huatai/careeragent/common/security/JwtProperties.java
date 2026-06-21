package com.huatai.careeragent.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@ConfigurationProperties(prefix = "career-agent.security")
@Validated
public record JwtProperties(
        @NotBlank @Size(min = 32) String jwtSecret,
        long jwtExpiresSeconds
) {
    public JwtProperties {
        if (jwtSecret != null && jwtSecret.startsWith("change-me")) {
            throw new IllegalArgumentException("JWT_SECRET must not use the documented placeholder");
        }
        if (jwtExpiresSeconds <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRES_SECONDS must be positive");
        }
    }
}
