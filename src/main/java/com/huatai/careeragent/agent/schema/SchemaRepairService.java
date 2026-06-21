package com.huatai.careeragent.agent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

@Service
public class SchemaRepairService {
    private static final Logger log = LoggerFactory.getLogger(SchemaRepairService.class);

    private final JsonSchemaValidator schemaValidator;
    private final LlmClient llmClient;

    public SchemaRepairService(JsonSchemaValidator schemaValidator, LlmClient llmClient) {
        this.schemaValidator = schemaValidator;
        this.llmClient = llmClient;
    }

    public RepairResult validateOrRepair(String schemaName, String rawOutput, String traceId) {
        return validateOrRepair(schemaName, rawOutput, traceId, llmClient::complete);
    }

    public RepairResult validateOrRepair(String schemaName, String rawOutput, String traceId,
                                         Function<LlmRequest, LlmResponse> repairCaller) {
        String normalized = stripCodeFence(rawOutput);
        SchemaValidationResult initial = schemaValidator.validate(schemaName, normalized);
        if (initial.valid()) {
            return new RepairResult(initial.value(), null, false);
        }
        log.warn(
                "Agent output schema validation failed: schema={}, errors={}, outputSummary={}, traceId={}",
                schemaName, initial.errors(), summarize(rawOutput), traceId
        );

        LlmRequest repairRequest = LlmRequest.secured(
                "You repair JSON. Return only one JSON object that exactly follows the supplied schema.",
                "Repair the invalid output. Do not add facts that are absent from the original output.",
                List.of(schemaValidator.schemaText(schemaName), rawOutput),
                traceId,
                true
        );
        LlmResponse repairResponse = repairCaller.apply(repairRequest);
        SchemaValidationResult repaired = schemaValidator.validate(schemaName, stripCodeFence(repairResponse.content()));
        if (!repaired.valid()) {
            log.warn(
                    "Agent output schema repair failed: schema={}, errors={}, outputSummary={}, traceId={}",
                    schemaName, repaired.errors(), summarize(repairResponse.content()), traceId
            );
            throw new SchemaOutputException("Model output could not be repaired", repaired.errors());
        }
        return new RepairResult(repaired.value(), repairResponse, true);
    }

    private String stripCodeFence(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > 0) {
                return trimmed.substring(firstLineEnd + 1, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    private String summarize(String value) {
        String safe = value == null ? "" : value;
        return "length=" + safe.length() + ",sha256=" + sha256(safe).substring(0, 16);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record RepairResult(JsonNode value, LlmResponse repairResponse, boolean repaired) {
    }
}
