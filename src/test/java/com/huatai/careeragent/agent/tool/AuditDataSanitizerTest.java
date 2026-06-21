package com.huatai.careeragent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDataSanitizerTest {
    private final AuditDataSanitizer sanitizer = new AuditDataSanitizer(new ObjectMapper());

    @Test
    void redactsDocumentBodiesAndSecretsRecursively() {
        Map<String, Object> sanitized = sanitizer.sanitize(Map.of(
                "content", "Private resume with phone and project history",
                "query", "Authorization Bearer abc.def-123",
                "nested", Map.of("apiKey", "sk-private-value-123456", "items", List.of(
                        "eyJheader.eyJpayload.signature", "safe"
                ))
        ));

        assertThat(sanitized.get("content").toString())
                .startsWith("[REDACTED_DOCUMENT length=")
                .contains("sha256=")
                .doesNotContain("Private resume");
        assertThat(sanitized.get("query")).isEqualTo("Authorization Bearer ***");
        Map<?, ?> nested = (Map<?, ?>) sanitized.get("nested");
        assertThat(nested.get("apiKey")).isEqualTo("***");
        assertThat(nested.get("items")).isEqualTo(List.of("***JWT***", "safe"));
    }

    @Test
    void summarizesMcpPayloadsInsteadOfPersistingRemoteContent() {
        Map<String, Object> sanitized = sanitizer.sanitize(Map.of(
                "mcpContent", List.of(Map.of("type", "text", "text", "remote private result")),
                "structuredContent", Map.of("candidateEmail", "private@example.com")
        ));

        assertThat(sanitized.get("mcpContent").toString()).startsWith("[REDACTED_DOCUMENT length=");
        assertThat(sanitized.get("structuredContent").toString()).startsWith("[REDACTED_DOCUMENT length=");
        assertThat(sanitized.toString()).doesNotContain("remote private result", "private@example.com");
    }
}
