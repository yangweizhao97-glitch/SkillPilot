package com.huatai.careeragent.tutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.agent.tool.PlannedToolCall;
import com.huatai.careeragent.agent.tool.RestrictedToolCallResult;
import com.huatai.careeragent.agent.tool.RestrictedToolCallingService;
import com.huatai.careeragent.agent.tool.SearchPublicInterviewKnowledgeTool;
import com.huatai.careeragent.agent.tool.SearchUserKnowledgeBaseTool;
import com.huatai.careeragent.agent.tool.ToolCallingPolicy;
import com.huatai.careeragent.agent.tool.ToolExecutionContext;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.document.DocumentRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class TutorService {
    private static final Logger log = LoggerFactory.getLogger(TutorService.class);
    private static final List<FileType> PRIVATE_SOURCES = List.of(
            FileType.RESUME, FileType.JD, FileType.NOTE, FileType.PROJECT_DOC
    );
    private static final int MAX_MODEL_PLANNED_TOOL_CALLS = 2;
    private static final int RECENT_CONTEXT_MESSAGES = 12;
    private static final int MAX_MEMORY_CHARS = 6000;
    private static final int MAX_MESSAGE_MEMORY_CHARS = 500;
    private static final int MAX_SOURCE_CONTENT_CHARS = 2400;
    private static final double MIN_PRIVATE_RELEVANCE = 0.60;
    private static final double MIN_PUBLIC_RELEVANCE = 0.70;

    private final TutorSessionRepository sessionRepository;
    private final TutorMessageRepository messageRepository;
    private final ResumeRepository resumeRepository;
    private final DocumentRepository documentRepository;
    private final JobRepository jobRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerEvaluationRepository evaluationRepository;
    private final LearningPlanRepository learningPlanRepository;
    private final KnowledgeSearchService knowledgeSearchService;
    private final PublicKnowledgeSearchService publicKnowledgeSearchService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final RestrictedToolCallingService toolCalling;
    private final TransactionTemplate transactions;

    public TutorService(TutorSessionRepository sessionRepository, TutorMessageRepository messageRepository,
                        ResumeRepository resumeRepository, DocumentRepository documentRepository,
                        JobRepository jobRepository,
                        InterviewQuestionRepository questionRepository,
                        InterviewAnswerEvaluationRepository evaluationRepository,
                        LearningPlanRepository learningPlanRepository,
                        KnowledgeSearchService knowledgeSearchService,
                        PublicKnowledgeSearchService publicKnowledgeSearchService, LlmClient llmClient,
                        ObjectMapper objectMapper, RestrictedToolCallingService toolCalling,
                        PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.resumeRepository = resumeRepository;
        this.documentRepository = documentRepository;
        this.jobRepository = jobRepository;
        this.questionRepository = questionRepository;
        this.evaluationRepository = evaluationRepository;
        this.learningPlanRepository = learningPlanRepository;
        this.knowledgeSearchService = knowledgeSearchService;
        this.publicKnowledgeSearchService = publicKnowledgeSearchService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.toolCalling = toolCalling;
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
            String traceId = "tutor_" + sessionId + "_" + UUID.randomUUID().toString().replace("-", "");
            SourceContext sources = toolCallingSources(work, content, traceId);
            String promptContext = promptContext(work, sources, content);
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

    private SourceContext toolCallingSources(TutorWork work, String content, String traceId) {
        try {
            List<PlannedToolCall> plannedCalls = planTutorTools(work, content, traceId);
            List<RestrictedToolCallResult> results = toolCalling.execute(plannedCalls,
                    new ToolCallingPolicy(AgentNames.TUTOR_AGENT,
                            Set.of(SearchUserKnowledgeBaseTool.NAME, SearchPublicInterviewKnowledgeTool.NAME),
                            MAX_MODEL_PLANNED_TOOL_CALLS),
                    ToolExecutionContext.tutorSession(work.session().getUserId(), work.session().getId(), traceId,
                            AgentNames.TUTOR_AGENT));
            return sourceContextFromToolResults(results);
        } catch (RuntimeException exception) {
            log.warn("Tutor restricted tool calling fell back to preset path: sessionId={}, reason={}",
                    work.session().getId(), exception.getMessage());
            return sources(work.session(), work.retrievalQuery());
        }
    }

    private List<PlannedToolCall> planTutorTools(TutorWork work, String content, String traceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("availableTools", List.of(
                Map.of("toolName", SearchUserKnowledgeBaseTool.NAME, "maxCalls", MAX_MODEL_PLANNED_TOOL_CALLS,
                        "purpose", "Search the user's private resume, JD, notes, and project documents."),
                Map.of("toolName", SearchPublicInterviewKnowledgeTool.NAME, "maxCalls", MAX_MODEL_PLANNED_TOOL_CALLS,
                        "purpose", "Search public interview knowledge and common interview answers.")
        ));
        payload.put("defaultPrivateSearch", privateSearchArguments(work.retrievalQuery(), Map.of()));
        payload.put("defaultPublicSearch", publicSearchArguments(work.session(), work.retrievalQuery(), Map.of()));
        payload.put("sessionMemory", work.memory());
        payload.put("recentConversation", work.history().stream().map(message -> Map.of(
                "role", message.getRole().name(), "content", message.getContent()
        )).toList());
        payload.put("currentMessage", content);
        var response = llmClient.complete(LlmRequest.secured(
                "You are a strict tool planner for a tutor agent.",
                """
                        Return strict JSON with this shape:
                        {"toolCalls":[{"toolName":"searchUserKnowledgeBase","arguments":{"query":"...","topK":5}}]}
                        Use only availableTools. Do not exceed maxCalls. Use an empty toolCalls array when no search is useful.
                        """,
                List.of(objectMapperWrite(payload)), traceId, true
        ));
        return parsePlannedToolCalls(response == null ? null : response.content(), work);
    }

    private List<PlannedToolCall> parsePlannedToolCalls(String content, TutorWork work) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Tutor tool planner returned empty content");
        }
        try {
            JsonNode toolCalls = objectMapper.readTree(content).path("toolCalls");
            if (!toolCalls.isArray()) {
                return List.of();
            }
            List<PlannedToolCall> calls = new ArrayList<>();
            for (JsonNode node : toolCalls) {
                String toolName = node.path("toolName").asText("");
                Map<String, Object> arguments = objectMapper.convertValue(
                        node.path("arguments"), new TypeReference<Map<String, Object>>() { });
                if (SearchUserKnowledgeBaseTool.NAME.equals(toolName)) {
                    arguments = privateSearchArguments(work.retrievalQuery(), arguments);
                } else if (SearchPublicInterviewKnowledgeTool.NAME.equals(toolName)) {
                    arguments = publicSearchArguments(work.session(), work.retrievalQuery(), arguments);
                }
                calls.add(new PlannedToolCall(toolName, arguments));
            }
            return List.copyOf(calls);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse tutor planned tool calls", exception);
        }
    }

    private Map<String, Object> privateSearchArguments(String query, Map<String, Object> planned) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", plannedString(planned, "query", query));
        arguments.put("sourceTypes", PRIVATE_SOURCES);
        arguments.put("topK", planned.getOrDefault("topK", 5));
        arguments.put("retrievalMode", RetrievalMode.HYBRID);
        return Map.copyOf(arguments);
    }

    private Map<String, Object> publicSearchArguments(TutorSession session, String query, Map<String, Object> planned) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", plannedString(planned, "query", query));
        if (session.getJobId() != null) {
            var job = jobRepository.findByIdAndUserId(session.getJobId(), session.getUserId()).orElse(null);
            if (job != null) {
                arguments.put("position", plannedString(planned, "position", job.getPosition()));
                arguments.put("company", plannedString(planned, "company", job.getCompany()));
            }
        }
        for (String key : List.of("industry", "experienceLevel", "interviewRound")) {
            Object value = planned.get(key);
            if (value instanceof String text && !text.isBlank()) {
                arguments.put(key, text);
            }
        }
        arguments.put("topK", planned.getOrDefault("topK", 4));
        arguments.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(arguments);
    }

    private String plannedString(Map<String, Object> planned, String key, String fallback) {
        Object value = planned.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private SourceContext sourceContextFromToolResults(List<RestrictedToolCallResult> results) {
        List<Map<String, Object>> context = new ArrayList<>();
        List<Map<String, Object>> citations = new ArrayList<>();
        for (RestrictedToolCallResult result : results) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("sourceType", "TOOL_RESULT");
            source.put("toolName", result.toolName());
            source.put("output", result.output());
            context.add(Map.copyOf(source));
            extractToolCitations(result, citations);
        }
        return new SourceContext(List.copyOf(context), List.copyOf(citations));
    }

    @SuppressWarnings("unchecked")
    private void extractToolCitations(RestrictedToolCallResult result, List<Map<String, Object>> citations) {
        Object items = result.output().get("items");
        if (!(items instanceof List<?> values)) {
            return;
        }
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> value = (Map<String, Object>) raw;
            String citationId = stringValue(value.get("citationId"));
            if (citationId == null || citationId.isBlank()) {
                continue;
            }
            if (SearchUserKnowledgeBaseTool.NAME.equals(result.toolName())) {
                citations.add(citation(citationId, "PRIVATE_" + stringValue(value.get("sourceType")),
                        stringValue(value.get("sourceTitle")), stringValue(value.get("sourceLocator")),
                        stringValue(value.get("content"))));
            } else if (SearchPublicInterviewKnowledgeTool.NAME.equals(result.toolName())) {
                citations.add(citation(citationId, "PUBLIC_INTERVIEW_KNOWLEDGE",
                        stringValue(value.get("sourceTitle")), stringValue(value.get("sourceUrl")),
                        stringValue(value.get("question"))));
            }
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String objectMapperWrite(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize tutor tool planning context", exception);
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
            String memory = compactMemory(session, history);
            List<TutorMessage> recent = bounded(history);
            boolean firstTurn = history.size() == 1;
            return new TutorWork(session, recent, memory, retrievalQuery(history, normalized), firstTurn);
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
        addAnchoredResume(session, context, citations);
        addAnchoredJob(session, context, citations);
        var knowledge = knowledgeSearchService.search(session.getUserId(),
                new KnowledgeSearchRequest(query, PRIVATE_SOURCES, 5, RetrievalMode.HYBRID));
        knowledge.items().stream().filter(item -> item.score() >= MIN_PRIVATE_RELEVANCE).forEach(item -> {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("citationId", item.citationId());
            source.put("sourceType", item.sourceType().name());
            source.put("title", item.sourceTitle() == null ? "用户资料" : item.sourceTitle());
            source.put("content", limit(item.content(), MAX_SOURCE_CONTENT_CHARS));
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
                new SearchRequest(query, null, position, company, null, null, 4));
        publicKnowledge.items().stream().filter(item -> item.score() >= MIN_PUBLIC_RELEVANCE).forEach(item -> {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("citationId", item.citationId());
            source.put("sourceType", "PUBLIC_INTERVIEW_KNOWLEDGE");
            source.put("title", item.sourceTitle());
            source.put("question", item.question());
            source.put("answerOutline", item.answerOutline());
            if (item.referenceAnswer() != null && !item.referenceAnswer().isBlank()) {
                source.put("referenceAnswer", limit(item.referenceAnswer(), MAX_SOURCE_CONTENT_CHARS));
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

    private void addAnchoredResume(TutorSession session, List<Map<String, Object>> context,
                                   List<Map<String, Object>> citations) {
        if (session.getResumeId() == null) return;
        var resume = resumeRepository.findByIdAndUserId(session.getResumeId(), session.getUserId()).orElseThrow();
        var document = documentRepository.findByIdAndUserId(resume.getDocumentId(), session.getUserId()).orElseThrow();
        String citationId = "resume_" + resume.getId();
        context.add(Map.of("citationId", citationId, "sourceType", "ANCHORED_RESUME",
                "title", resume.getTitle(), "content", limit(document.getContentText(), MAX_SOURCE_CONTENT_CHARS)));
        citations.add(citation(citationId, "ANCHORED_RESUME", resume.getTitle(), null,
                snippet(document.getContentText())));
    }

    private void addAnchoredJob(TutorSession session, List<Map<String, Object>> context,
                                List<Map<String, Object>> citations) {
        if (session.getJobId() == null) return;
        var job = jobRepository.findByIdAndUserId(session.getJobId(), session.getUserId()).orElseThrow();
        String citationId = "job_" + job.getId();
        String title = (job.getCompany() == null || job.getCompany().isBlank() ? "" : job.getCompany() + " · ")
                + job.getPosition();
        context.add(Map.of("citationId", citationId, "sourceType", "ANCHORED_JOB", "title", title,
                "content", limit(job.getJdText(), MAX_SOURCE_CONTENT_CHARS)));
        citations.add(citation(citationId, "ANCHORED_JOB", title, null, snippet(job.getJdText())));
    }

    private String promptContext(TutorWork work, SourceContext sources, String currentMessage) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionMemory", work.memory());
            payload.put("memoryRevision", work.session().getMemoryRevision());
            payload.put("conversationState", work.firstTurn() ? "FIRST_TURN" : "CONTINUING");
            payload.put("recentConversation", work.history().stream().map(message -> Map.of(
                            "role", message.getRole().name(), "content", message.getContent()
                    )).toList());
            payload.put("currentMessage", currentMessage);
            payload.put("retrievalQuery", work.retrievalQuery());
            payload.put("retrievedSources", sources.context());
            return objectMapper.writeValueAsString(payload);
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
        value.put("citationId", id); value.put("sourceType", type); value.put("title", title == null ? "" : title);
        if (locator != null && !locator.isBlank()) value.put("sourceLocator", locator);
        value.put("snippet", snippet == null ? "" : snippet);
        return Map.copyOf(value);
    }

    private String snippet(String content) {
        if (content == null) return "";
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "…";
    }

    private List<TutorMessage> bounded(List<TutorMessage> messages) {
        return List.copyOf(messages.subList(Math.max(0, messages.size() - RECENT_CONTEXT_MESSAGES), messages.size()));
    }

    private String compactMemory(TutorSession session, List<TutorMessage> messages) {
        int cutoff = Math.max(0, messages.size() - RECENT_CONTEXT_MESSAGES);
        List<TutorMessage> candidates = messages.subList(0, cutoff).stream()
                .filter(message -> message.getSequenceNo() > session.getMemoryThroughSequence()).toList();
        if (candidates.isEmpty()) return session.getMemorySummary();
        StringBuilder summary = new StringBuilder(session.getMemorySummary());
        for (TutorMessage message : candidates) {
            if (!summary.isEmpty()) summary.append('\n');
            summary.append(message.getRole() == TutorMessageRole.USER ? "用户：" : "导师：")
                    .append(limit(normalize(message.getContent()), MAX_MESSAGE_MEMORY_CHARS));
            if (!message.getCitations().isEmpty()) {
                summary.append("（引用：").append(message.getCitations().stream()
                        .map(value -> String.valueOf(value.get("citationId"))).limit(4)
                        .collect(java.util.stream.Collectors.joining("、"))).append("）");
            }
        }
        String memory = summary.length() <= MAX_MEMORY_CHARS ? summary.toString()
                : "较早对话已继续压缩：\n…" + summary.substring(summary.length() - MAX_MEMORY_CHARS + 12);
        int through = candidates.getLast().getSequenceNo();
        session.updateMemory(memory, through);
        return memory;
    }

    private String retrievalQuery(List<TutorMessage> history, String current) {
        return limit(current, 1200);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
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
                session.isProcessing(), session.getMemoryRevision(), session.getMemoryThroughSequence(),
                messages, session.getCreatedAt(), session.getUpdatedAt());
    }

    public record CreateTutorSessionRequest(String title, Long resumeId, Long jobId, Long questionId,
                                            Long evaluationId, Long learningPlanId) { }
    private record TutorWork(TutorSession session, List<TutorMessage> history, String memory,
                             String retrievalQuery, boolean firstTurn) { }
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
                                       int memoryRevision, int memoryThroughSequence,
                                       List<TutorMessageResponse> messages, Instant createdAt, Instant updatedAt) { }
    public record TutorSessionSummary(Long sessionId, String title, boolean processing,
                                      Instant createdAt, Instant updatedAt) {
        static TutorSessionSummary from(TutorSession session) {
            return new TutorSessionSummary(session.getId(), session.getTitle(), session.isProcessing(),
                    session.getCreatedAt(), session.getUpdatedAt());
        }
    }
}
