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
    void validatesInteractiveInterviewTurnDecision() {
        assertThat(validator.validate("interview_turn.schema.json",
                "{\"followUp\":true,\"message\":\"请具体说明事务边界。\"}").valid()).isTrue();
        assertThat(validator.validate("interview_turn.schema.json",
                "{\"followUp\":\"yes\",\"message\":\"追问\"}").valid()).isFalse();
        assertThat(validator.validate("interview_turn.schema.json",
                "{\"followUp\":false}").valid()).isFalse();
    }

    @Test
    void validatesInterviewAnswerEvaluation() {
        String valid = """
                {"schemaVersion":"1.0","overallScore":82,"dimensions":[
                  {"key":"accuracy","label":"准确性","score":85,"rationale":"事实准确"},
                  {"key":"relevance","label":"相关性","score":82,"rationale":"紧扣题意"},
                  {"key":"depth","label":"深度","score":78,"rationale":"覆盖主要机制"},
                  {"key":"communication","label":"表达","score":84,"rationale":"结构清晰"}
                ],"strengths":["回答准确"],"improvements":["补充边界条件"],
                "improvedAnswer":"补充边界条件后的示例回答。","followUp":false,"followUpQuestion":""}
                """;
        assertThat(validator.validate("interview_answer_evaluation.schema.json", valid).valid()).isTrue();
        assertThat(validator.validate("interview_answer_evaluation.schema.json",
                valid.replace("\"overallScore\":82", "\"overallScore\":101")).valid()).isFalse();
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
