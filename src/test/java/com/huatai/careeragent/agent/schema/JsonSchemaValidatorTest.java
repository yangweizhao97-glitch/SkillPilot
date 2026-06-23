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
    void validatesSprintLearningPlanContract() {
        String sprint = """
                {"schemaVersion":"2.0","planMode":"SPRINT","summary":"三天冲刺事务与消息队列", "targetRole":"Java 后端",
                "interviewDate":"2026-06-25","daysRemaining":3,"availableHoursPerDay":2,
                "priorities":[{"priority":1,"skill":"事务","gap":"案例不足","evidence":"面试评分"}],
                "dailyPlans":[{"day":1,"date":"2026-06-22","focus":"事务","actions":["复盘传播行为"],"questions":["REQUIRES_NEW 如何工作？"],"deliverables":["口述稿"]}],
                "practiceQuestions":["如何设计事务边界？"],"mockInterviewSchedule":[{"day":3,"focus":"完整模拟"}],
                "likelyInterviewQuestions":[
                  {"question":"REQUIRES_NEW 如何工作？","whyAsked":"岗位关注事务一致性，简历项目提到审计日志。","knowledgePoints":["事务传播"],"answerStrategy":["先定义事务边界"],"referenceAnswer":"REQUIRES_NEW 会开启独立事务，适合隔离审计写入。","practiceTasks":["准备项目口述稿"],"sourceMaterials":["最终报告"]},
                  {"question":"事务失效如何排查？","whyAsked":"面试常追问 Spring 代理调用边界。","knowledgePoints":["代理失效"],"answerStrategy":["说明调用链"],"referenceAnswer":"先检查是否同类内部调用、方法可见性和异常回滚规则。","practiceTasks":["画出调用链"],"sourceMaterials":["公共面经"]}
                ],
                "sourceMaterials":["最终报告"],"adjustmentReason":"评分显示事务深度不足。","successMetrics":["五分钟讲清事务案例"]}
                """;
        assertThat(validator.validate("learning_plan_sprint.schema.json", sprint).valid()).isTrue();
        assertThat(validator.validate("learning_plan_sprint.schema.json",
                sprint.replace("\"SPRINT\"", "\"LONG_TERM\"")).valid()).isFalse();
    }

    @Test
    void validatesPublicKnowledgeExtractionContract() {
        String valid = """
                {"experiences":[{"industry":"互联网","company":"示例公司","position":"Java 后端",
                "experienceLevel":null,"interviewRound":"二面","summary":"系统设计面试","tags":["Java"],
                "eventDate":null,"questions":[{"question":"如何设计限流？","questionType":"SYSTEM_DESIGN",
                "difficulty":"MEDIUM","knowledgePoints":["令牌桶"],"answerOutline":["明确目标"],
                "referenceAnswer":"按容量设计","scoringRubric":[],"commonMistakes":[],"followUpCandidates":[]}]}]}
                """;
        assertThat(validator.validate("public_interview_knowledge_extraction.schema.json", valid).valid()).isTrue();
        assertThat(validator.validate("public_interview_knowledge_extraction.schema.json",
                valid.replace("\"SYSTEM_DESIGN\"", "\"UNKNOWN\"")).valid()).isFalse();
    }

    @Test
    void validatesPublicKnowledgeQualityReviewContract() {
        String valid = """
                {"decision":"ACCEPT","technicalAccuracy":90,"interviewPlausibility":85,"currency":80,
                "answerQuality":88,"issues":[],"reviewSummary":"题目与答案合理。","correctedReferenceAnswer":null}
                """;
        assertThat(validator.validate("public_interview_quality_review.schema.json", valid).valid()).isTrue();
        assertThat(validator.validate("public_interview_quality_review.schema.json",
                valid.replace("\"technicalAccuracy\":90", "\"technicalAccuracy\":101")).valid()).isFalse();
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
