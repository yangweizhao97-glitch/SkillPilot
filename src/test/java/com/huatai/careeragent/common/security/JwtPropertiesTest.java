package com.huatai.careeragent.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {
    @Test
    void rejectsDocumentedPlaceholderSecret() {
        assertThatThrownBy(() -> new JwtProperties("change-me-before-running-in-production", 7200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsNonPositiveExpiry() {
        assertThatThrownBy(() -> new JwtProperties("a-real-secret-that-is-at-least-32-characters", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }
}
