package com.huatai.careeragent.agent.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonSchemaValidatorTest {
    private final JsonSchemaValidator validator = new JsonSchemaValidator(new ObjectMapper());

    @Test
    void acceptsValidJobMatchResult() {
        assertThat(validator.validate("job_match_result.schema.json", validJobMatch()).valid()).isTrue();
    }

    @Test
    void rejectsMissingWrongTypeAndAdditionalProperties() {
        String missing = validJobMatch().replace("\"summary\":\"Good fit\",", "");
        String wrongType = validJobMatch().replace("\"matchScore\":85", "\"matchScore\":\"85\"");
        String additional = validJobMatch().replace("\"citations\":[]", "\"citations\":[],\"internal\":true");

        assertThat(validator.validate("job_match_result.schema.json", missing).valid()).isFalse();
        assertThat(validator.validate("job_match_result.schema.json", wrongType).valid()).isFalse();
        assertThat(validator.validate("job_match_result.schema.json", additional).valid()).isFalse();
    }

    @Test
    void repairsInvalidOutputWithLlmAndRejectsFailedRepair() {
        LlmClient llmClient = mock(LlmClient.class);
        SchemaRepairService repairService = new SchemaRepairService(validator, llmClient);
        when(llmClient.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LlmResponse(
                        validJobMatch(), "TEST", "mock", "stop",
                        new LlmResponse.TokenUsage(10, 20, 30), 1, "request-1"
                ));

        SchemaRepairService.RepairResult repaired = repairService.validateOrRepair(
                "job_match_result.schema.json", "{\"matchScore\":85}", "trace-1"
        );

        assertThat(repaired.repaired()).isTrue();
        assertThat(repaired.value().path("summary").asText()).isEqualTo("Good fit");

        when(llmClient.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LlmResponse("{}", "TEST", "mock", "stop", LlmResponse.TokenUsage.empty(), 1, "request-2"));
        assertThatThrownBy(() -> repairService.validateOrRepair(
                "job_match_result.schema.json", "not-json", "trace-2"
        )).isInstanceOf(SchemaOutputException.class)
                .extracting(exception -> ((SchemaOutputException) exception).getCode())
                .isEqualTo("AGENT_OUTPUT_SCHEMA_INVALID");
    }

    private String validJobMatch() {
        return """
                {
                  "matchScore":85,
                  "summary":"Good fit",
                  "strengths":["Java"],
                  "weaknesses":[],
                  "missingSkills":[],
                  "suggestedResumeChanges":["Add metrics"],
                  "citations":[]
                }
                """;
    }
}
