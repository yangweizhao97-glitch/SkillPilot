package com.huatai.careeragent.agent.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JsonSchemaValidator {
    private static final Set<String> ALLOWED_SCHEMAS = Set.of(
            "job_match_result.schema.json",
            "resume_analysis_result.schema.json",
            "interview_questions.schema.json",
            "interview_turn.schema.json",
            "interview_answer_evaluation.schema.json",
            "interview_session_review.schema.json",
            "learning_plan_sprint.schema.json",
            "learning_plan_long_term.schema.json"
    );

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SchemaValidationResult validate(String schemaName, String rawJson) {
        try {
            return validate(schemaName, objectMapper.readTree(rawJson));
        } catch (JsonProcessingException exception) {
            return SchemaValidationResult.invalid(List.of("Output is not valid JSON: " + exception.getOriginalMessage()));
        }
    }

    public SchemaValidationResult validate(String schemaName, JsonNode value) {
        Set<ValidationMessage> messages = schema(schemaName).validate(value);
        if (messages.isEmpty()) {
            return SchemaValidationResult.valid(value);
        }
        return SchemaValidationResult.invalid(messages.stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .limit(20)
                .toList());
    }

    public String schemaText(String schemaName) {
        requireAllowed(schemaName);
        try {
            return new ClassPathResource("schemas/" + schemaName).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read JSON schema: " + schemaName, exception);
        }
    }

    private JsonSchema schema(String schemaName) {
        requireAllowed(schemaName);
        return schemas.computeIfAbsent(schemaName, this::loadSchema);
    }

    private JsonSchema loadSchema(String schemaName) {
        try (InputStream input = new ClassPathResource("schemas/" + schemaName).getInputStream()) {
            return schemaFactory.getSchema(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load JSON schema: " + schemaName, exception);
        }
    }

    private void requireAllowed(String schemaName) {
        if (!ALLOWED_SCHEMAS.contains(schemaName)) {
            throw new IllegalArgumentException("Unknown JSON schema: " + schemaName);
        }
    }
}
