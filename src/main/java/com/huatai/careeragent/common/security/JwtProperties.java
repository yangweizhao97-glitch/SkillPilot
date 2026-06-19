package com.huatai.careeragent.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "career-agent.security")
public record JwtProperties(
        String jwtSecret,
        long jwtExpiresSeconds
) {
}
