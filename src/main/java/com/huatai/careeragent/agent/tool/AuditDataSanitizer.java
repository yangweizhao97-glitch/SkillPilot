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

@Component
public class AuditDataSanitizer {
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "password", "passwd", "token", "jwt", "secret", "apikey", "api_key", "authorization"
    );

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
                } else {
                    redact(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redact);
        }
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
        return SENSITIVE_MARKERS.stream().anyMatch(normalized::contains);
    }
}
