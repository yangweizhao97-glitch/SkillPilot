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
import java.util.regex.Pattern;

@Component
public class ToolModelContextSanitizer {
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "password", "passwd", "token", "jwt", "secret", "apikey", "api_key", "authorization"
    );
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._~-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[a-zA-Z0-9_-]+\\.eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");
    private static final Pattern API_KEY = Pattern.compile("(?i)sk-[a-z0-9_-]{12,}");

    private final ObjectMapper objectMapper;

    public ToolModelContextSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> sanitize(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode root = objectMapper.valueToTree(value);
        redactAndLimit(root);
        if (!root.isObject()) {
            return Map.of("value", objectMapper.convertValue(root, Object.class));
        }
        return objectMapper.convertValue(root, new TypeReference<>() { });
    }

    private void redactAndLimit(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.properties().forEach(entry -> {
                if (isSensitive(entry.getKey())) {
                    objectNode.put(entry.getKey(), "***");
                } else if (entry.getValue().isTextual()) {
                    objectNode.put(entry.getKey(), limit(redactSecrets(entry.getValue().asText())));
                } else {
                    redactAndLimit(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            for (int index = 0; index < arrayNode.size(); index++) {
                JsonNode item = arrayNode.get(index);
                if (item.isTextual()) {
                    arrayNode.set(index, objectMapper.getNodeFactory().textNode(limit(redactSecrets(item.asText()))));
                } else {
                    redactAndLimit(item);
                }
            }
        }
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
        return SENSITIVE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private String redactSecrets(String value) {
        String redacted = BEARER.matcher(value).replaceAll("Bearer ***");
        redacted = JWT.matcher(redacted).replaceAll("***JWT***");
        return API_KEY.matcher(redacted).replaceAll("***API_KEY***");
    }

    private String limit(String value) {
        if (value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH) + "...[TRUNCATED length=" + value.length() + "]";
    }
}
