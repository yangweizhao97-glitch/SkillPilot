package com.huatai.careeragent.interview;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.resume.Resume;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.user.User;
import com.huatai.careeragent.user.UserRepository;
import com.huatai.careeragent.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class InterviewReviewServiceIntegrationTest {
    @Autowired private InterviewReviewService reviewService;
    @Autowired private InteractiveInterviewService interviewService;
    @Autowired private InterviewSessionReviewRepository reviewRepository;
    @Autowired private InterviewAnswerEvaluationRepository evaluationRepository;
    @Autowired private InterviewMessageRepository messageRepository;
    @Autowired private InterviewSessionRepository sessionRepository;
    @Autowired private InterviewQuestionRepository questionRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private UserRepository userRepository;
    @MockitoBean private LlmClient llmClient;

    @AfterEach
    void cleanUp() {
        reviewRepository.deleteAll();
        evaluationRepository.deleteAll();
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
        questionRepository.deleteAll();
        jobRepository.deleteAll();
        resumeRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void generatesOneSessionScopedReviewAndCanonicalizesScores() {
        User user = user("review");
        Document document = documentRepository.save(new Document(
                user.getId(), null, FileType.RESUME, "Backend resume", "Java and Spring", Map.of()
        ));
        Resume resume = resumeRepository.save(new Resume(user.getId(), document.getId(), "Backend"));
        Job job = jobRepository.save(new Job(user.getId(), null, "Acme", "Backend Engineer", "Build APIs"));
        questionRepository.save(new InterviewQuestion(user.getId(), resume.getId(), job.getId(), null,
                "How do transactions work?", QuestionType.TECHNICAL, QuestionDifficulty.MEDIUM,
                List.of("Propagation", "Isolation"), List.of(), "General question"));
        when(llmClient.complete(any()))
                .thenReturn(response(evaluationJson()))
                .thenReturn(response(reviewJson()));

        var session = interviewService.create(user.getId(), resume.getId(), job.getId());
        interviewService.answer(user.getId(), session.sessionId(), "事务通过传播行为和隔离级别控制边界。");
        interviewService.finish(user.getId(), session.sessionId());

        var first = reviewService.generate(user.getId(), session.sessionId());
        var second = reviewService.generate(user.getId(), session.sessionId());

        assertThat(first.reviewId()).isEqualTo(second.reviewId());
        assertThat(first.overallScore()).isEqualTo(72);
        assertThat(first.evaluatedAnswers()).isEqualTo(1);
        assertThat(first.generationSource()).isEqualTo("LLM");
        assertThat(reviewRepository.count()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) first.result().get("dimensions");
        assertThat(dimensions).extracting(item -> ((Number) item.get("score")).intValue())
                .containsExactly(70, 76, 65, 80);
        verify(llmClient, times(2)).complete(any());

        User intruder = user("intruder");
        assertThatThrownBy(() -> reviewService.get(intruder.getId(), session.sessionId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void fallsBackToValidatedEvaluationsWhenReviewModelFails() {
        User user = user("fallback");
        Document document = documentRepository.save(new Document(
                user.getId(), null, FileType.RESUME, "Backend resume", "Java and Spring", Map.of()
        ));
        Resume resume = resumeRepository.save(new Resume(user.getId(), document.getId(), "Backend"));
        Job job = jobRepository.save(new Job(user.getId(), null, "Acme", "Backend Engineer", "Build APIs"));
        questionRepository.save(new InterviewQuestion(user.getId(), resume.getId(), job.getId(), null,
                "How do transactions work?", QuestionType.TECHNICAL, QuestionDifficulty.MEDIUM,
                List.of("Propagation", "Isolation"), List.of(), "General question"));
        when(llmClient.complete(any()))
                .thenReturn(response(evaluationJson()))
                .thenThrow(new IllegalStateException("model unavailable"));

        var session = interviewService.create(user.getId(), resume.getId(), job.getId());
        interviewService.answer(user.getId(), session.sessionId(), "事务通过传播行为和隔离级别控制边界。");
        interviewService.finish(user.getId(), session.sessionId());

        var review = reviewService.generate(user.getId(), session.sessionId());

        assertThat(review.generationSource()).isEqualTo("FALLBACK");
        assertThat(review.overallScore()).isEqualTo(72);
        assertThat(review.result()).containsKeys("strengths", "gaps", "actionPlan", "recommendedPracticeQuestions");
    }

    private User user(String prefix) {
        return userRepository.save(new User(prefix + "-" + UUID.randomUUID() + "@example.com",
                "hash", prefix, UserRole.USER));
    }

    private LlmResponse response(String content) {
        return new LlmResponse(content, "TEST", "mock", "stop", LlmResponse.TokenUsage.empty(), 1, "request");
    }

    private String evaluationJson() {
        return """
                {"schemaVersion":"1.0","overallScore":1,"dimensions":[
                  {"key":"accuracy","label":"准确性","score":70,"rationale":"概念正确"},
                  {"key":"relevance","label":"相关性","score":76,"rationale":"紧扣问题"},
                  {"key":"depth","label":"深度","score":65,"rationale":"案例不足"},
                  {"key":"communication","label":"表达","score":80,"rationale":"表达清晰"}
                ],"strengths":["概念准确"],"improvements":["补充真实案例"],
                "improvedAnswer":"结合项目案例说明传播行为。","followUp":false,"followUpQuestion":""}
                """;
    }

    private String reviewJson() {
        return """
                {"schemaVersion":"1.0","overallScore":1,"summary":"基础概念较稳，需要增加案例深度。",
                "dimensions":[
                  {"key":"accuracy","label":"准确性","score":1,"assessment":"概念准确"},
                  {"key":"relevance","label":"相关性","score":1,"assessment":"紧扣题意"},
                  {"key":"depth","label":"深度","score":1,"assessment":"案例不足"},
                  {"key":"communication","label":"表达","score":1,"assessment":"表达清晰"}
                ],"strengths":["概念准确"],"gaps":["缺少项目案例"],
                "actionPlan":[{"priority":1,"action":"重写事务案例","reason":"提升回答深度"}],
                "recommendedPracticeQuestions":["请说明一次事务失效问题的排查过程。"]}
                """;
    }
}
