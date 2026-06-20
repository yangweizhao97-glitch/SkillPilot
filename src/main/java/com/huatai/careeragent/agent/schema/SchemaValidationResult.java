package com.huatai.careeragent.agent.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record SchemaValidationResult(boolean valid, JsonNode value, List<String> errors) {
    public static SchemaValidationResult valid(JsonNode value) {
        return new SchemaValidationResult(true, value, List.of());
    }

    public static SchemaValidationResult invalid(List<String> errors) {
        return new SchemaValidationResult(false, null, List.copyOf(errors));
    }
}
