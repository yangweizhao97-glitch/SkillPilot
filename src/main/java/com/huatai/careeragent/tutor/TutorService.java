package com.huatai.careeragent.tutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.interview.InterviewAnswerEvaluation;
import com.huatai.careeragent.interview.InterviewAnswerEvaluationRepository;
import com.huatai.careeragent.interview.InterviewQuestion;
import com.huatai.careeragent.interview.InterviewQuestionRepository;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchRequest;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeSearchService;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeSearchDtos.SearchRequest;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeSearchService;
import com.huatai.careeragent.knowledge.retrieval.RetrievalMode;
import com.huatai.careeragent.learning.LearningPlan;
import com.huatai.careeragent.learning.LearningPlanRepository;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.PromptCatalog;
import com.huatai.careeragent.resume.ResumeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class TutorService {
    private static final List<FileType> PRIVATE_SOURCES = List.of(
            FileType.RESUME, FileType.JD, FileType.NOTE, FileType.PROJECT_DOC
    );
    private static final int MAX_CONTEXT_MESSAGES = 12;

    private final TutorSessionRepository sessionRepository;
    private final TutorMessageRepository messageRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerEvaluationRepository evaluationRepository;
    private final LearningPlanRepository learningPlanRepository;
    private final KnowledgeSearchService knowledgeSearchService;
    private final PublicKnowledgeSearchService publicKnowledgeSearchService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public TutorService(TutorSessionRepository sessionRepository, TutorMessageRepository messageRepository,
                        ResumeRepository resumeRepository, JobRepository jobRepository,
                        InterviewQuestionRepository questionRepository,
                        InterviewAnswerEvaluationRepository evaluationRepository,
                        LearningPlanRepository learningPlanRepository,
                        KnowledgeSearchService knowledgeSearchService,
                        PublicKnowledgeSearchService publicKnowledgeSearchService, LlmClient llmClient,
                        ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.questionRepository = questionRepository;
        this.evaluationRepository = evaluationRepository;
        this.learningPlanRepository = learningPlanRepository;
        this.knowledgeSearchService = knowledgeSearchService;
        this.publicKnowledgeSearchService = publicKnowledgeSearchService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public TutorSessionResponse create(Long userId, CreateTutorSessionRequest request) {
        validateAnchors(userId, request);
        String title = request.title() == null || request.title().isBlank() ? "AI 答疑" : request.title().trim();
        TutorSession saved = sessionRepository.save(new TutorSession(
                userId, title, request.resumeId(), request.jobId(), request.questionId(),
                request.evaluationId(), request.learningPlanId()
        ));
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<TutorSessionSummary> list(Long userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId).stream()
                .map(TutorSessionSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public TutorSessionResponse get(Long userId, Long sessionId) {
        return response(requireSession(userId, sessionId));
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        sessionRepository.delete(requireSession(userId, sessionId));
    }

    public TutorSessionResponse messageStreaming(Long userId, Long sessionId, String content,
                                                 Consumer<String> onDelta) {
        TutorWork work = begin(userId, sessionId, content);
        try {
            SourceContext sources = sources(work.session(), content);
            String promptContext = promptContext(work, sources);
            String traceId = "tutor_" + sessionId + "_" + UUID.randomUUID().toString().replace("-", "");
            StringBuilder streamed = new StringBuilder();
            var llmResponse = llmClient.stream(LlmRequest.secured(
                    PromptCatalog.TUTOR.systemPrompt(), PromptCatalog.TUTOR.instruction(),
                    List.of(promptContext), traceId, false
            ), delta -> {
                streamed.append(delta);
                onDelta.accept(delta);
            });
            String answer = llmResponse == null || llmResponse.content() == null
                    ? streamed.toString() : llmResponse.content();
            if (answer == null || answer.isBlank()) throw new IllegalStateException("Tutor returned an empty answer");
            return complete(work, answer.trim(), usedCitations(answer, sources.citations()));
        } catch (RuntimeException exception) {
            abort(work);
            throw exception;
        }
    }

    private TutorWork begin(Long userId, Long sessionId, String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("TUTOR_MESSAGE_REQUIRED", "问题不能为空", HttpStatus.BAD_REQUEST);
        }
        return Objects.requireNonNull(transactions.execute(status -> {
            TutorSession session = requireLockedSession(userId, sessionId);
            if (session.isProcessing() && !session.processingIsStale(Instant.now().minus(Duration.ofMinutes(3)))) {
                throw new BusinessException("TUTOR_MESSAGE_PROCESSING", "上一条问题仍在处理中", HttpStatus.CONFLICT);
            }
            session.beginProcessing();
            addMessage(session, TutorMessageRole.USER, normalized, List.of());
            List<TutorMessage> history = messageRepository
                    .findByUserIdAndSessionIdOrderBySequenceNoAsc(userId, sessionId);
            return new TutorWork(session, bounded(history));
        }));
    }

    private TutorSessionResponse complete(TutorWork work, String answer, List<Map<String, Object>> citations) {
        return Objects.requireNonNull(transactions.execute(status -> {
            TutorSession session = requireLockedSession(work.session().getUserId(), work.session().getId());
            if (!session.isProcessing()) {
                throw new BusinessException("TUTOR_TURN_CHANGED", "答疑会话状态已经变化", HttpStatus.CONFLICT);
            }
            addMessage(session, TutorMessageRole.ASSISTANT, answer, citations);
            session.endProcessing();
            return response(session);
        }));
    }

    private void abort(TutorWork work) {
        transactions.executeWithoutResult(status -> {
            TutorSession session = requireLockedSession(work.session().getUserId(), work.session().getId());
            session.endProcessing();
        });
    }

    private SourceContext sources(TutorSession session, String query) {
        List<Map<String, Object>> context = new ArrayList<>();
        List<Map<String, Object>> citations = new ArrayList<>();
        var knowledge = knowledgeSearchService.search(session.getUserId(),
                new KnowledgeSearchRequest(query, PRIVATE_SOURCES, 6, RetrievalMode.HYBRID));
        knowledge.items().forEach(item -> {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("citationId", item.citationId());
            source.put("sourceType", item.sourceType().name());
            source.put("title", item.sourceTitle() == null ? "用户资料" : item.sourceTitle());
            source.put("content", item.content());
            context.add(Map.copyOf(source));
            citations.add(citation(item.citationId(), "PRIVATE_" + item.sourceType().name(),
                    item.sourceTitle(), item.sourceLocator(), snippet(item.content())));
        });
        String company = null;
        String position = null;
        if (session.getJobId() != null) {
            var job = jobRepository.findByIdAndUserId(session.getJobId(), session.getUserId()).orElseThrow();
            company = job.getCompany();
            position = job.getPosition();
        }
        var publicKnowledge = publicKnowledgeSearchService.search(
                new SearchRequest(query, null, position, company, null, null, 6));
        publicKnowledge.items().forEach(item -> {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("citationId", item.citationId());
            source.put("sourceType", "PUBLIC_INTERVIEW_KNOWLEDGE");
            source.put("title", item.sourceTitle());
            source.put("question", item.question());
            source.put("answerOutline", item.answerOutline());
            if (item.referenceAnswer() != null && !item.referenceAnswer().isBlank()) {
                source.put("referenceAnswer", item.referenceAnswer());
            }
            context.add(Map.copyOf(source));
            citations.add(citation(item.citationId(), "PUBLIC_INTERVIEW_KNOWLEDGE",
                    item.sourceTitle(), item.sourceUrl(), snippet(item.question())));
        });
        if (session.getQuestionId() != null) {
            InterviewQuestion question = questionRepository.findByIdAndUserId(
                    session.getQuestionId(), session.getUserId()).orElseThrow();
            String id = "question_" + question.getId();
            context.add(Map.of("citationId", id, "sourceType", "INTERVIEW_QUESTION",
                    "question", question.getQuestionText(), "expectedPoints", question.getExpectedPoints()));
            citations.add(citation(id, "INTERVIEW_QUESTION", "关联面试题", null, question.getQuestionText()));
        }
        if (session.getEvaluationId() != null) {
            InterviewAnswerEvaluation evaluation = evaluationRepository.findByIdAndUserId(
                    session.getEvaluationId(), session.getUserId()).orElseThrow();
            String id = "evaluation_" + evaluation.getId();
            context.add(Map.of("citationId", id, "sourceType", "INTERVIEW_EVALUATION",
                    "score", evaluation.getOverallScore(), "result", evaluation.getResultJson()));
            citations.add(citation(id, "INTERVIEW_EVALUATION", "回答评分", null,
                    "总分 " + evaluation.getOverallScore() + "，包含逐维度改进建议"));
        }
        if (session.getLearningPlanId() != null) {
            LearningPlan plan = learningPlanRepository.findByIdAndUserId(
                    session.getLearningPlanId(), session.getUserId()).orElseThrow();
            String id = "learning_plan_" + plan.getId();
            context.add(Map.of("citationId", id, "sourceType", "LEARNING_PLAN", "plan", plan.getResultJson()));
            citations.add(citation(id, "LEARNING_PLAN", "关联学习计划", null, "当前学习计划中的目标与行动"));
        }
        return new SourceContext(List.copyOf(context), List.copyOf(citations));
    }

    private String promptContext(TutorWork work, SourceContext sources) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "conversation", work.history().stream().map(message -> Map.of(
                            "role", message.getRole().name(), "content", message.getContent()
                    )).toList(),
                    "retrievedSources", sources.context()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize tutor context", exception);
        }
    }

    private List<Map<String, Object>> usedCitations(String answer, List<Map<String, Object>> available) {
        return available.stream().filter(item -> answer.contains("[" + item.get("citationId") + "]"))
                .limit(8).toList();
    }

    private Map<String, Object> citation(String id, String type, String title, String locator, String snippet) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("citationId", id); value.put("sourceType", type); value.put("title", title);
        if (locator != null && !locator.isBlank()) value.put("sourceLocator", locator);
        value.put("snippet", snippet);
        return Map.copyOf(value);
    }

    private String snippet(String content) {
        if (content == null) return "";
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "…";
    }

    private List<TutorMessage> bounded(List<TutorMessage> messages) {
        return List.copyOf(messages.subList(Math.max(0, messages.size() - MAX_CONTEXT_MESSAGES), messages.size()));
    }

    private TutorMessage addMessage(TutorSession session, TutorMessageRole role, String content,
                                    List<Map<String, Object>> citations) {
        return messageRepository.save(new TutorMessage(session.getUserId(), session.getId(), role, content,
                citations, messageRepository.maxSequence(session.getId()) + 1));
    }

    private TutorSession requireSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId).orElseThrow(() ->
                new BusinessException("TUTOR_SESSION_NOT_FOUND", "答疑会话不存在", HttpStatus.NOT_FOUND));
    }

    private TutorSession requireLockedSession(Long userId, Long sessionId) {
        return sessionRepository.findLockedByIdAndUserId(sessionId, userId).orElseThrow(() ->
                new BusinessException("TUTOR_SESSION_NOT_FOUND", "答疑会话不存在", HttpStatus.NOT_FOUND));
    }

    private void validateAnchors(Long userId, CreateTutorSessionRequest request) {
        if (request.resumeId() != null && resumeRepository.findByIdAndUserId(request.resumeId(), userId).isEmpty())
            throw new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND);
        if (request.jobId() != null && jobRepository.findByIdAndUserId(request.jobId(), userId).isEmpty())
            throw new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND);
        if (request.questionId() != null && questionRepository.findByIdAndUserId(request.questionId(), userId).isEmpty())
            throw new BusinessException("INTERVIEW_QUESTION_NOT_FOUND", "面试题不存在", HttpStatus.NOT_FOUND);
        if (request.evaluationId() != null && evaluationRepository.findByIdAndUserId(request.evaluationId(), userId).isEmpty())
            throw new BusinessException("INTERVIEW_EVALUATION_NOT_FOUND", "回答评分不存在", HttpStatus.NOT_FOUND);
        if (request.learningPlanId() != null && learningPlanRepository.findByIdAndUserId(request.learningPlanId(), userId).isEmpty())
            throw new BusinessException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在", HttpStatus.NOT_FOUND);
    }

    private TutorSessionResponse response(TutorSession session) {
        List<TutorMessageResponse> messages = messageRepository
                .findByUserIdAndSessionIdOrderBySequenceNoAsc(session.getUserId(), session.getId()).stream()
                .map(TutorMessageResponse::from).toList();
        return new TutorSessionResponse(session.getId(), session.getTitle(), session.getResumeId(), session.getJobId(),
                session.getQuestionId(), session.getEvaluationId(), session.getLearningPlanId(),
                session.isProcessing(), messages, session.getCreatedAt(), session.getUpdatedAt());
    }

    public record CreateTutorSessionRequest(String title, Long resumeId, Long jobId, Long questionId,
                                            Long evaluationId, Long learningPlanId) { }
    private record TutorWork(TutorSession session, List<TutorMessage> history) { }
    private record SourceContext(List<Map<String, Object>> context, List<Map<String, Object>> citations) { }
    public record TutorMessageResponse(Long messageId, TutorMessageRole role, String content,
                                       List<Map<String, Object>> citations, int sequenceNo, Instant createdAt) {
        static TutorMessageResponse from(TutorMessage message) {
            return new TutorMessageResponse(message.getId(), message.getRole(), message.getContent(),
                    message.getCitations(), message.getSequenceNo(), message.getCreatedAt());
        }
    }
    public record TutorSessionResponse(Long sessionId, String title, Long resumeId, Long jobId, Long questionId,
                                       Long evaluationId, Long learningPlanId, boolean processing,
                                       List<TutorMessageResponse> messages, Instant createdAt, Instant updatedAt) { }
    public record TutorSessionSummary(Long sessionId, String title, boolean processing,
                                      Instant createdAt, Instant updatedAt) {
        static TutorSessionSummary from(TutorSession session) {
            return new TutorSessionSummary(session.getId(), session.getTitle(), session.isProcessing(),
                    session.getCreatedAt(), session.getUpdatedAt());
        }
    }
}
