package com.huatai.careeragent.knowledge.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.interview.QuestionDifficulty;
import com.huatai.careeragent.interview.QuestionType;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicKnowledgeQualityServiceTest {
    @Test
    void acceptsStrongQuestionAndDerivesMultiSourceConfidenceOnServer() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        KnowledgeSourceRepository sources = mock(KnowledgeSourceRepository.class);
        InterviewExperienceRepository experiences = mock(InterviewExperienceRepository.class);
        PublicInterviewQuestionRepository questions = mock(PublicInterviewQuestionRepository.class);
        PublicQuestionEvidenceRepository evidence = mock(PublicQuestionEvidenceRepository.class);
        LlmClient llm = mock(LlmClient.class);
        SchemaRepairService repair = mock(SchemaRepairService.class);
        KnowledgeSource source = new KnowledgeSource(KnowledgeSource.SourceType.WEB, "NOWCODER",
                "https://example.com", "Java 面经", null, "hash",
                KnowledgeSource.CopyrightStatus.PUBLIC_SUMMARY, new BigDecimal("0.8"), 1L);
        InterviewExperience experience = new InterviewExperience(null, "互联网", "示例公司", "Java 后端",
                "3-5年", "二面", "讨论了限流", List.of("Java"), null);
        PublicInterviewQuestion question = new PublicInterviewQuestion(null, "如何设计限流？", "qhash",
                QuestionType.SYSTEM_DESIGN, QuestionDifficulty.MEDIUM, List.of("令牌桶"), List.of("明确容量"),
                "根据容量配置令牌桶", List.of(), List.of(), List.of());
        String reviewJson = """
                {"decision":"ACCEPT","technicalAccuracy":90,"interviewPlausibility":88,"currency":85,
                "answerQuality":86,"issues":[],"reviewSummary":"题目与答案合理。","correctedReferenceAnswer":null}
                """;
        when(sources.findById(1L)).thenReturn(Optional.of(source));
        when(experiences.findBySourceIdOrderByIdAsc(1L)).thenReturn(List.of(experience));
        when(questions.findByExperienceIdOrderByIdAsc(null)).thenReturn(List.of(question));
        when(evidence.sourceCount("qhash")).thenReturn(2);
        when(llm.complete(any())).thenReturn(new LlmResponse(reviewJson, "test", "test", "stop",
                LlmResponse.TokenUsage.empty(), 1, "request"));
        when(repair.validateOrRepair(eq("public_interview_quality_review.schema.json"), eq(reviewJson), anyString()))
                .thenReturn(new SchemaRepairService.RepairResult(mapper.readTree(reviewJson), null, false));
        PublicKnowledgeQualityService service = new PublicKnowledgeQualityService(sources, experiences, questions,
                evidence, llm, repair, mapper);

        var result = service.review(1L);

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(question.getQualityStatus()).isEqualTo(PublicInterviewQuestion.QualityStatus.ACCEPTED);
        assertThat(question.getConfidenceLabel())
                .isEqualTo(PublicInterviewQuestion.ConfidenceLabel.MULTI_SOURCE_VERIFIED);
        assertThat(question.getQualityScore()).isGreaterThanOrEqualTo(85);
    }
}
