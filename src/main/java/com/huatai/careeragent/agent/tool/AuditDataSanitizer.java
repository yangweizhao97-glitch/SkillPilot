package com.huatai.careeragent.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
public class AuditDataSanitizer {
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "password", "passwd", "token", "jwt", "secret", "apikey", "api_key", "authorization"
    );
    private static final Set<String> DOCUMENT_MARKERS = Set.of("content", "description", "jd_text", "parsed_text");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._~-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[a-zA-Z0-9_-]+\\.eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");
    private static final Pattern API_KEY = Pattern.compile("(?i)sk-[a-z0-9_-]{12,}");

    private final ObjectMapper objectMapper;

    public AuditDataSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> sanitize(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode root = objectMapper.valueToTree(value);
        redact(root);
        if (!root.isObject()) {
            return Map.of("value", objectMapper.convertValue(root, Object.class));
        }
        return objectMapper.convertValue(root, new TypeReference<>() { });
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.properties().forEach(entry -> {
                if (isSensitive(entry.getKey())) {
                    objectNode.put(entry.getKey(), "***");
                } else if (isDocumentContent(entry.getKey()) && entry.getValue().isTextual()) {
                    objectNode.put(entry.getKey(), summarize(entry.getValue().asText()));
                } else if (entry.getValue().isTextual()) {
                    objectNode.put(entry.getKey(), redactSecrets(entry.getValue().asText()));
                } else {
                    redact(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            for (int index = 0; index < arrayNode.size(); index++) {
                JsonNode item = arrayNode.get(index);
                if (item.isTextual()) {
                    arrayNode.set(index, objectMapper.getNodeFactory().textNode(redactSecrets(item.asText())));
                } else {
                    redact(item);
                }
            }
        }
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
        return SENSITIVE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private boolean isDocumentContent(String key) {
        return DOCUMENT_MARKERS.contains(key.toLowerCase(Locale.ROOT).replace("-", "_"));
    }

    private String summarize(String value) {
        return "[REDACTED_DOCUMENT length=" + value.length() + ",sha256=" + sha256(value).substring(0, 16) + "]";
    }

    private String redactSecrets(String value) {
        String redacted = BEARER.matcher(value).replaceAll("Bearer ***");
        redacted = JWT.matcher(redacted).replaceAll("***JWT***");
        return API_KEY.matcher(redacted).replaceAll("***API_KEY***");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
