package com.huatai.careeragent.knowledge.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeDtos.SourceResponse;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicKnowledgeExtractionServiceTest {
    @Test
    void validatesModelJsonBeforeCreatingPendingSource() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LlmClient llm = mock(LlmClient.class);
        SchemaRepairService repair = mock(SchemaRepairService.class);
        PublicKnowledgeAdminService admin = mock(PublicKnowledgeAdminService.class);
        String json = """
                {"experiences":[{"industry":"互联网","company":"示例公司","position":"Java 后端",
                "experienceLevel":"3-5年","interviewRound":"二面","summary":"系统设计面试",
                "tags":["Java"],"eventDate":null,"questions":[{"question":"如何设计限流？",
                "questionType":"SYSTEM_DESIGN","difficulty":"MEDIUM","knowledgePoints":["令牌桶"],
                "answerOutline":["明确目标"],"referenceAnswer":"按容量设计","scoringRubric":[],
                "commonMistakes":[],"followUpCandidates":[]}]}]}
                """;
        when(llm.complete(any())).thenReturn(new LlmResponse(json, "test", "test", "stop",
                LlmResponse.TokenUsage.empty(), 1, "request"));
        when(repair.validateOrRepair(eq("public_interview_knowledge_extraction.schema.json"), eq(json), anyString()))
                .thenReturn(new SchemaRepairService.RepairResult(mapper.readTree(json), null, false));
        SourceResponse expected = new SourceResponse(1L, "面经", "MANUAL", null,
                KnowledgeSource.ReviewStatus.PENDING, new BigDecimal("0.8"), 1, 1, 0, 0, 0, null, null);
        when(admin.create(eq(9L), any())).thenReturn(expected);
        PublicKnowledgeExtractionService service = new PublicKnowledgeExtractionService(llm, repair, admin, mapper);

        var actual = service.extractAndCreate(9L, new PublicKnowledgeExtractionService.ExtractRequest(
                KnowledgeSource.SourceType.MANUAL, "MANUAL", null, "面经", null,
                KnowledgeSource.CopyrightStatus.AUTHORIZED, new BigDecimal("0.8"), "二面问了限流"));

        assertThat(actual.reviewStatus()).isEqualTo(KnowledgeSource.ReviewStatus.PENDING);
        verify(admin).create(eq(9L), argThat(request -> request.experiences().getFirst().questions().size() == 1));
    }
}
