package com.huatai.careeragent.interview;

import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
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
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
class InteractiveInterviewServiceIntegrationTest {
    @Autowired private InteractiveInterviewService service;
    @Autowired private InterviewAnswerEvaluationRepository evaluationRepository;
    @Autowired private InterviewMessageRepository messageRepository;
    @Autowired private InterviewSessionRepository sessionRepository;
    @Autowired private InterviewSessionMemoryRepository memoryRepository;
    @Autowired private InterviewMemoryService memoryService;
    @Autowired private InterviewQuestionRepository questionRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private LlmClient llmClient;

    @AfterEach
    void cleanUp() {
        evaluationRepository.deleteAll();
        memoryRepository.deleteAll();
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
        var firstMemory = memoryService.get(user.getId(), resume.getId(), job.getId());
        assertThat(firstMemory.available()).isTrue();
        assertThat(firstMemory.answerCount()).isEqualTo(1);
        assertThat(firstMemory.averageScore()).isEqualTo(72);
        assertThat(firstMemory.strengths()).contains("抓住了一致性目标");
        assertThat(firstMemory.improvementAreas()).contains("补充传播行为和隔离级别");

        var advanced = service.answer(user.getId(), created.sessionId(), "支持 REQUIRED 和 REQUIRES_NEW。");
        assertThat(advanced.currentQuestion()).isEqualTo(2);
        assertThat(advanced.messages().getLast().content()).isEqualTo("How would you design a task queue?");

        assertThat(advanced.evaluations()).hasSize(2);
        assertThat(memoryService.get(user.getId(), resume.getId(), job.getId()).answerCount()).isEqualTo(2);
        when(llmClient.complete(any())).thenThrow(new IllegalStateException("model unavailable"));
        var degraded = service.answer(user.getId(), created.sessionId(), "使用持久化队列并由工作线程消费。");
        assertThat(degraded.status()).isEqualTo(InterviewSessionStatus.FINISHED);
        assertThat(degraded.evaluations()).hasSize(2);
        assertThat(degraded.messages()).extracting(InteractiveInterviewService.InterviewMessageResponse::content)
                .contains("使用持久化队列并由工作线程消费。");
        doReturn(new LlmResponse(
                evaluationJson("请补充一个生产案例。"),
                "TEST", "mock", "stop", LlmResponse.TokenUsage.empty(), 1, "request-2"
        )).when(llmClient).complete(any());
        var nextSession = service.create(user.getId(), resume.getId(), job.getId());
        service.answer(user.getId(), nextSession.sessionId(), "我会先说明事务边界和失败恢复策略。");
        var crossSessionMemory = memoryService.get(user.getId(), resume.getId(), job.getId());
        assertThat(crossSessionMemory.sessionCount()).isEqualTo(2);
        assertThat(crossSessionMemory.answerCount()).isEqualTo(3);
        User intruder = userRepository.save(new User(
                "intruder-" + UUID.randomUUID() + "@example.com", "hash", "Intruder", UserRole.USER
        ));
        assertThatThrownBy(() -> service.get(intruder.getId(), created.sessionId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> memoryService.get(intruder.getId(), resume.getId(), job.getId()))
                .isInstanceOf(BusinessException.class);
        ArgumentCaptor<LlmRequest> requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient, times(8)).complete(requests.capture());
        List<LlmRequest> evaluationRequests = requests.getAllValues().stream()
                .filter(request -> request.messages().get(1).content().contains("evaluationGuide"))
                .toList();
        assertThat(evaluationRequests).hasSize(4);
        assertThat(evaluationRequests.get(1).messages().get(1).content())
                .contains("interviewMemory", "answerCount", "1")
                .doesNotContain("事务保证一致性");
        assertThat(evaluationRequests.get(3).messages().get(1).content())
                .contains("interviewMemory", "answerCount", "2")
                .doesNotContain("支持 REQUIRED 和 REQUIRES_NEW");

        memoryService.clear(user.getId(), resume.getId(), job.getId());
        assertThat(memoryService.get(user.getId(), resume.getId(), job.getId()).available()).isFalse();

        var invalidSession = service.create(user.getId(), resume.getId(), job.getId());
        var clarification = service.answer(user.getId(), invalidSession.sessionId(), "不知道");
        assertThat(clarification.currentQuestion()).isEqualTo(1);
        assertThat(clarification.evaluations().getFirst().result())
                .containsEntry("answerDisposition", "NO_ANSWER")
                .containsEntry("nextAction", "CLARIFY");
        assertThat(clarification.messages().getLast().content()).contains("请先具体说明");
        assertThat(memoryService.get(user.getId(), resume.getId(), job.getId()).available()).isFalse();
        verify(llmClient, times(8)).complete(any());

        doReturn(new LlmResponse(
                adaptiveEvaluationJson(), "TEST", "mock", "stop",
                LlmResponse.TokenUsage.empty(), 1, "request-adaptive"
        )).when(llmClient).complete(any());
        var secondClarification = service.answer(user.getId(), invalidSession.sessionId(), "我会先定义事务边界。");
        assertThat(secondClarification.currentQuestion()).isEqualTo(1);
        var thirdClarification = service.answer(user.getId(), invalidSession.sessionId(), "再说明传播行为。");
        assertThat(thirdClarification.currentQuestion()).isEqualTo(1);
        var forcedAdvance = service.answer(user.getId(), invalidSession.sessionId(), "最后补充失败恢复与验证。");
        assertThat(forcedAdvance.currentQuestion()).isEqualTo(2);
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

    private String adaptiveEvaluationJson() {
        return """
                {"schemaVersion":"1.0","overallScore":58,"dimensions":[
                  {"key":"accuracy","label":"准确性","score":60,"rationale":"部分正确"},
                  {"key":"relevance","label":"相关性","score":70,"rationale":"围绕问题"},
                  {"key":"depth","label":"深度","score":40,"rationale":"缺少细节"},
                  {"key":"communication","label":"表达","score":65,"rationale":"表达清楚"}
                ],"strengths":["方向正确"],"improvements":["补充具体机制"],
                "improvedAnswer":"先说明边界，再说明传播行为和失败恢复。",
                "followUp":true,"followUpQuestion":"请继续补充具体机制。",
                "answerDisposition":"PARTIAL","nextAction":"DEEPEN","missingPoints":["具体机制"]}
                """;
    }

    private InterviewQuestion question(Long userId, Long resumeId, Long jobId, String text) {
        return new InterviewQuestion(userId, resumeId, jobId, null, text, QuestionType.TECHNICAL,
                QuestionDifficulty.MEDIUM, List.of("Clear reasoning"), List.of(), "General question");
    }
}
