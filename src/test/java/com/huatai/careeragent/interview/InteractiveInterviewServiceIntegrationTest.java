package com.huatai.careeragent.interview;

import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.common.error.BusinessException;
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
class InteractiveInterviewServiceIntegrationTest {
    @Autowired private InteractiveInterviewService service;
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
    void createsConversationAllowsOneFollowUpAndFinishes() {
        User user = userRepository.save(new User(
                "interview-" + UUID.randomUUID() + "@example.com", "hash", "Candidate", UserRole.USER
        ));
        Document document = documentRepository.save(new Document(
                user.getId(), null, FileType.RESUME, "Backend resume", "Java and Spring experience", Map.of()
        ));
        Resume resume = resumeRepository.save(new Resume(user.getId(), document.getId(), "Backend"));
        Job job = jobRepository.save(new Job(user.getId(), null, "Acme", "Backend Engineer", "Build APIs"));
        questionRepository.saveAll(List.of(
                question(user.getId(), resume.getId(), job.getId(), "How do transactions work?"),
                question(user.getId(), resume.getId(), job.getId(), "How would you design a task queue?")
        ));
        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                evaluationJson("请具体说明事务传播行为。"),
                "TEST", "mock", "stop", LlmResponse.TokenUsage.empty(), 1, "request-1"
        ));

        var created = service.create(user.getId(), resume.getId(), job.getId());
        assertThat(created.messages()).hasSize(1);

        var followedUp = service.answer(user.getId(), created.sessionId(), "事务保证一致性。 ");
        assertThat(followedUp.currentQuestion()).isEqualTo(1);
        assertThat(followedUp.messages()).extracting(InteractiveInterviewService.InterviewMessageResponse::content)
                .contains("请具体说明事务传播行为。");
        assertThat(followedUp.evaluations()).hasSize(1);
        assertThat(followedUp.evaluations().getFirst().overallScore()).isEqualTo(72);
        assertThat(followedUp.evaluations().getFirst().answerMessageId()).isNotNull();

        var advanced = service.answer(user.getId(), created.sessionId(), "支持 REQUIRED 和 REQUIRES_NEW。");
        assertThat(advanced.currentQuestion()).isEqualTo(2);
        assertThat(advanced.messages().getLast().content()).isEqualTo("How would you design a task queue?");

        assertThat(advanced.evaluations()).hasSize(2);
        when(llmClient.complete(any())).thenThrow(new IllegalStateException("model unavailable"));
        var degraded = service.answer(user.getId(), created.sessionId(), "使用持久化队列并由工作线程消费。");
        assertThat(degraded.status()).isEqualTo(InterviewSessionStatus.FINISHED);
        assertThat(degraded.evaluations()).hasSize(2);
        assertThat(degraded.messages()).extracting(InteractiveInterviewService.InterviewMessageResponse::content)
                .contains("使用持久化队列并由工作线程消费。");
        User intruder = userRepository.save(new User(
                "intruder-" + UUID.randomUUID() + "@example.com", "hash", "Intruder", UserRole.USER
        ));
        assertThatThrownBy(() -> service.get(intruder.getId(), created.sessionId()))
                .isInstanceOf(BusinessException.class);
        verify(llmClient, times(3)).complete(any());
    }

    private String evaluationJson(String followUpQuestion) {
        return """
                {"schemaVersion":"1.0","overallScore":72,"dimensions":[
                  {"key":"accuracy","label":"准确性","score":70,"rationale":"方向正确但缺少传播细节"},
                  {"key":"relevance","label":"相关性","score":76,"rationale":"围绕问题作答"},
                  {"key":"depth","label":"深度","score":65,"rationale":"需要具体机制"},
                  {"key":"communication","label":"表达","score":80,"rationale":"表达简洁"}
                ],"strengths":["抓住了一致性目标"],"improvements":["补充传播行为和隔离级别"],
                "improvedAnswer":"事务通过传播行为和隔离级别协调一致性边界。",
                "followUp":true,"followUpQuestion":"%s"}
                """.formatted(followUpQuestion);
    }

    private InterviewQuestion question(Long userId, Long resumeId, Long jobId, String text) {
        return new InterviewQuestion(userId, resumeId, jobId, null, text, QuestionType.TECHNICAL,
                QuestionDifficulty.MEDIUM, List.of("Clear reasoning"), List.of(), "General question");
    }
}
