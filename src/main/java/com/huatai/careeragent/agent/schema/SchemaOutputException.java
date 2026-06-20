package com.huatai.careeragent.agent.schema;

import java.util.List;

public class SchemaOutputException extends RuntimeException {
    private final String code;
    private final List<String> validationErrors;

    public SchemaOutputException(String message, List<String> validationErrors) {
        super(message);
        this.code = "AGENT_OUTPUT_SCHEMA_INVALID";
        this.validationErrors = List.copyOf(validationErrors);
    }

    public String getCode() { return code; }
    public List<String> getValidationErrors() { return validationErrors; }
}
