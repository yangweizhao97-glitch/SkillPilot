package com.huatai.careeragent.interview;

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
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.knowledge.retrieval.RetrievalMode;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.PromptCatalog;
import com.huatai.careeragent.resume.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class InteractiveInterviewService {
    private static final Logger log = LoggerFactory.getLogger(InteractiveInterviewService.class);
    private static final String CLOSING_MESSAGE = "本轮面试已结束。你的回答已经保存，可以返回会话列表查看记录。";
    private static final int MAX_ADAPTIVE_FOLLOW_UPS = 3;
    private static final int MAX_MODEL_PLANNED_TOOL_CALLS = 1;
    private static final List<FileType> PRIVATE_SOURCES = List.of(
            FileType.RESUME, FileType.JD, FileType.NOTE, FileType.PROJECT_DOC
    );
    private static final Map<String, Integer> SCORE_WEIGHTS = Map.of(
            "accuracy", 35, "relevance", 25, "depth", 25, "communication", 15
    );

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final InterviewAnswerEvaluationRepository evaluationRepository;
    private final InterviewQuestionRepository questionRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final LlmClient llmClient;
    private final SchemaRepairService schemaRepairService;
    private final ObjectMapper objectMapper;
    private final InterviewMemoryService memoryService;
    private final RestrictedToolCallingService toolCalling;
    private final TransactionTemplate transactions;

    public InteractiveInterviewService(InterviewSessionRepository sessionRepository,
                                       InterviewMessageRepository messageRepository,
                                       InterviewAnswerEvaluationRepository evaluationRepository,
                                       InterviewQuestionRepository questionRepository,
                                       ResumeRepository resumeRepository, JobRepository jobRepository,
                                       LlmClient llmClient, SchemaRepairService schemaRepairService,
                                       ObjectMapper objectMapper, InterviewMemoryService memoryService,
                                       RestrictedToolCallingService toolCalling,
                                       PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.evaluationRepository = evaluationRepository;
        this.questionRepository = questionRepository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.llmClient = llmClient;
        this.schemaRepairService = schemaRepairService;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        this.toolCalling = toolCalling;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public InterviewSessionResponse create(Long userId, Long resumeId, Long jobId) {
        requireResources(userId, resumeId, jobId);
        List<InterviewQuestion> available = questionRepository
                .findByUserIdAndResumeIdAndJobIdOrderByCreatedAtDescIdDesc(userId, resumeId, jobId);
        if (available.isEmpty()) {
            throw new BusinessException("INTERVIEW_QUESTIONS_REQUIRED",
                    "请先完成职业分析并生成面试题", HttpStatus.CONFLICT);
        }
        Long latestTaskId = available.stream().map(InterviewQuestion::getTaskId)
                .filter(Objects::nonNull).findFirst().orElse(null);
        List<InterviewQuestion> selected = latestTaskId == null
                ? available.reversed()
                : questionRepository.findByUserIdAndTaskIdOrderByCreatedAtAscIdAsc(userId, latestTaskId);
        List<Long> questionIds = selected.stream()
                .limit(6)
                .map(InterviewQuestion::getId)
                .toList();
        InterviewSession session = sessionRepository.save(new InterviewSession(userId, resumeId, jobId, questionIds));
        InterviewQuestion first = requireQuestion(userId, session.currentQuestionId());
        addMessage(session, first.getId(), InterviewMessageRole.INTERVIEWER, first.getQuestionText());
        return response(session);
    }

    @Transactional(readOnly = true)
    public List<InterviewSessionSummary> list(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(InterviewSessionSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewSessionResponse get(Long userId, Long sessionId) {
        return response(requireSession(userId, sessionId));
    }

    public InterviewSessionResponse answer(Long userId, Long sessionId, String answer) {
        TurnWork work = beginTurn(userId, sessionId, answer);
        try {
            AnswerEvaluationDecision decision = evaluateAnswer(work);
            return completeTurn(work, decision, decision.followUpQuestion());
        } catch (RuntimeException exception) {
            abortTurn(work);
            throw exception;
        }
    }

    public InterviewSessionResponse answerStreaming(Long userId, Long sessionId, String answer,
                                                    Consumer<String> onDelta) {
        TurnWork work = beginTurn(userId, sessionId, answer);
        try {
            AnswerEvaluationDecision decision = evaluateAnswer(work);
            String followUp = canFollowUp(work, decision)
                    ? streamFollowUp(work, decision, onDelta) : "";
            return completeTurn(work, decision, followUp);
        } catch (RuntimeException exception) {
            abortTurn(work);
            throw exception;
        }
    }

    @Transactional
    public InterviewSessionResponse finish(Long userId, Long sessionId) {
        InterviewSession session = requireSessionForUpdate(userId, sessionId);
        if (session.isProcessingAnswer()) {
            throw new BusinessException("INTERVIEW_ANSWER_PROCESSING", "当前回答仍在处理中", HttpStatus.CONFLICT);
        }
        if (session.getStatus() == InterviewSessionStatus.IN_PROGRESS) {
            session.finish();
            addMessage(session, session.currentQuestionId(), InterviewMessageRole.INTERVIEWER, CLOSING_MESSAGE);
        }
        return response(session);
    }

    private AnswerEvaluationDecision evaluateAnswer(TurnWork work) {
        if (isNonAnswer(work.answer())) return nonAnswerDecision(work);
        try {
            String traceId = "interview_" + work.sessionId() + "_" + UUID.randomUUID().toString().replace("-", "");
            String context = objectMapper.writeValueAsString(Map.of(
                    "question", work.questionText(),
                    "expectedPoints", work.expectedPoints(),
                    "evaluationGuide", work.evaluationGuide(),
                    "candidateAnswer", work.answer(),
                    "interviewMemory", memoryService.get(
                            work.userId(), work.resumeId(), work.jobId()).promptContext(),
                    "toolEvidence", toolEvidence(work, traceId)
            ));
            var response = llmClient.complete(LlmRequest.secured(
                    PromptCatalog.ANSWER_EVALUATION.systemPrompt(),
                    PromptCatalog.ANSWER_EVALUATION.instruction(),
                    List.of(context), traceId, true
            ));
            var validated = schemaRepairService.validateOrRepair(
                    "interview_answer_evaluation.schema.json", response.content(), traceId
            ).value();
            Map<String, Object> result = objectMapper.convertValue(validated, new TypeReference<>() { });
            int overallScore = canonicalScore(validated, result);
            result.put("overallScore", overallScore);
            boolean adaptive = validated.hasNonNull("answerDisposition") || validated.hasNonNull("nextAction");
            InterviewAnswerDisposition disposition = disposition(validated, overallScore);
            InterviewNextAction nextAction = nextAction(validated, disposition,
                    validated.path("followUp").asBoolean(false));
            List<String> missingPoints = strings(validated.path("missingPoints"));
            result.put("answerDisposition", disposition.name());
            result.put("nextAction", nextAction.name());
            result.put("missingPoints", missingPoints);
            result.put("followUp", nextAction != InterviewNextAction.NEXT);
            if (nextAction == InterviewNextAction.NEXT) result.put("followUpQuestion", "");
            return new AnswerEvaluationDecision(nextAction != InterviewNextAction.NEXT,
                    validated.path("followUpQuestion").asText(""), result, overallScore,
                    disposition, nextAction, adaptive);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize interview evaluation", exception);
        } catch (RuntimeException exception) {
            log.warn("Interview answer evaluation failed; advancing to next question: sessionId={}, reason={}",
                    work.sessionId(), exception.getMessage());
            return new AnswerEvaluationDecision(false, "", Map.of(), null,
                    InterviewAnswerDisposition.COMPLETE, InterviewNextAction.NEXT, false);
        }
    }

    private List<RestrictedToolCallResult> toolEvidence(TurnWork work, String traceId) {
        try {
            List<PlannedToolCall> plannedCalls = planInterviewTools(work, traceId);
            return toolCalling.execute(plannedCalls,
                    new ToolCallingPolicy(AgentNames.INTERACTIVE_INTERVIEW_AGENT,
                            Set.of(SearchUserKnowledgeBaseTool.NAME, SearchPublicInterviewKnowledgeTool.NAME),
                            MAX_MODEL_PLANNED_TOOL_CALLS),
                    ToolExecutionContext.interviewSession(work.userId(), work.sessionId(), traceId,
                            AgentNames.INTERACTIVE_INTERVIEW_AGENT));
        } catch (RuntimeException exception) {
            log.warn("Interview restricted tool calling skipped: sessionId={}, reason={}",
                    work.sessionId(), exception.getMessage());
            return List.of();
        }
    }

    private List<PlannedToolCall> planInterviewTools(TurnWork work, String traceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("availableTools", List.of(
                Map.of("toolName", SearchPublicInterviewKnowledgeTool.NAME, "maxCalls", MAX_MODEL_PLANNED_TOOL_CALLS,
                        "purpose", "Find public interview knowledge relevant to the current question."),
                Map.of("toolName", SearchUserKnowledgeBaseTool.NAME, "maxCalls", MAX_MODEL_PLANNED_TOOL_CALLS,
                        "purpose", "Find private resume, JD, note, or project context relevant to the answer.")
        ));
        payload.put("question", work.questionText());
        payload.put("expectedPoints", work.expectedPoints());
        payload.put("candidateAnswer", work.answer());
        payload.put("defaultPrivateSearch", privateSearchArguments(work, Map.of()));
        payload.put("defaultPublicSearch", publicSearchArguments(work, Map.of()));
        var response = llmClient.complete(LlmRequest.secured(
                "You are a strict tool planner for an interactive interview evaluator.",
                """
                        Return strict JSON with this shape:
                        {"toolCalls":[{"toolName":"searchPublicInterviewKnowledge","arguments":{"query":"...","topK":4}}]}
                        Use only availableTools. Do not exceed maxCalls. Use an empty toolCalls array when no search is useful.
                        """,
                List.of(writeJson(payload)), traceId, true
        ));
        return parsePlannedToolCalls(response == null ? null : response.content(), work);
    }

    private List<PlannedToolCall> parsePlannedToolCalls(String content, TurnWork work) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Interview tool planner returned empty content");
        }
        try {
            JsonNode toolCalls = objectMapper.readTree(content).path("toolCalls");
            if (!toolCalls.isArray()) {
                return List.of();
            }
            List<PlannedToolCall> calls = new java.util.ArrayList<>();
            for (JsonNode node : toolCalls) {
                String toolName = node.path("toolName").asText("");
                Map<String, Object> arguments = objectMapper.convertValue(
                        node.path("arguments"), new TypeReference<Map<String, Object>>() { });
                if (SearchUserKnowledgeBaseTool.NAME.equals(toolName)) {
                    arguments = privateSearchArguments(work, arguments);
                } else if (SearchPublicInterviewKnowledgeTool.NAME.equals(toolName)) {
                    arguments = publicSearchArguments(work, arguments);
                }
                calls.add(new PlannedToolCall(toolName, arguments));
            }
            return List.copyOf(calls);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse interview planned tool calls", exception);
        }
    }

    private Map<String, Object> privateSearchArguments(TurnWork work, Map<String, Object> planned) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", plannedString(planned, "query", work.questionText() + " " + work.answer()));
        arguments.put("sourceTypes", PRIVATE_SOURCES);
        arguments.put("topK", planned.getOrDefault("topK", 4));
        arguments.put("retrievalMode", RetrievalMode.HYBRID);
        return Map.copyOf(arguments);
    }

    private Map<String, Object> publicSearchArguments(TurnWork work, Map<String, Object> planned) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", plannedString(planned, "query", work.questionText()));
        var job = jobRepository.findByIdAndUserId(work.jobId(), work.userId()).orElse(null);
        if (job != null) {
            arguments.put("position", plannedString(planned, "position", job.getPosition()));
            arguments.put("company", plannedString(planned, "company", job.getCompany()));
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize interview tool planning context", exception);
        }
    }

    private AnswerEvaluationDecision nonAnswerDecision(TurnWork work) {
        String focus = work.expectedPoints().isEmpty() ? "你会如何分析这个问题"
                : work.expectedPoints().getFirst();
        String followUp = "你的回答还没有提供足够信息。请先具体说明“" + focus + "”。";
        List<Map<String, Object>> dimensions = List.of(
                dimension("accuracy", "准确性", "当前回答没有可核验的技术内容。"),
                dimension("relevance", "相关性", "当前回答尚未回应题目。"),
                dimension("depth", "深度", "需要补充具体原理、步骤或案例。"),
                dimension("communication", "表达", "请用完整句子说明你的判断。")
        );
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("schemaVersion", "1.0");
        result.put("overallScore", 0);
        result.put("dimensions", dimensions);
        result.put("strengths", List.of());
        result.put("improvements", List.of("先直接回答问题，再补充原因和具体案例。"));
        result.put("improvedAnswer", "可以从“" + focus + "”开始，说明做法、原因和结果。");
        result.put("followUp", true);
        result.put("followUpQuestion", followUp);
        result.put("answerDisposition", InterviewAnswerDisposition.NO_ANSWER.name());
        result.put("nextAction", InterviewNextAction.CLARIFY.name());
        result.put("missingPoints", work.expectedPoints());
        return new AnswerEvaluationDecision(true, followUp, result, 0,
                InterviewAnswerDisposition.NO_ANSWER, InterviewNextAction.CLARIFY, true);
    }

    private Map<String, Object> dimension(String key, String label, String rationale) {
        return Map.of("key", key, "label", label, "score", 0,
                "weight", SCORE_WEIGHTS.get(key), "rationale", rationale);
    }

    private boolean isNonAnswer(String answer) {
        String normalized = answer.replaceAll("[\\p{Punct}\\s，。！？、；：]", "").toLowerCase();
        return normalized.length() < 4 || List.of("不知道", "不会", "不清楚", "没了解", "你好", "测试")
                .stream().anyMatch(normalized::equals);
    }

    private InterviewAnswerDisposition disposition(JsonNode value, int score) {
        try {
            if (value.hasNonNull("answerDisposition")) {
                return InterviewAnswerDisposition.valueOf(value.path("answerDisposition").asText());
            }
        } catch (IllegalArgumentException ignored) { }
        if (score >= 75) return InterviewAnswerDisposition.COMPLETE;
        if (score >= 45) return InterviewAnswerDisposition.PARTIAL;
        return InterviewAnswerDisposition.INCORRECT;
    }

    private InterviewNextAction nextAction(JsonNode value, InterviewAnswerDisposition disposition,
                                           boolean legacyFollowUp) {
        try {
            if (value.hasNonNull("nextAction")) {
                return InterviewNextAction.valueOf(value.path("nextAction").asText());
            }
        } catch (IllegalArgumentException ignored) { }
        if (!legacyFollowUp) return InterviewNextAction.NEXT;
        return disposition == InterviewAnswerDisposition.INCORRECT
                ? InterviewNextAction.CORRECT : InterviewNextAction.DEEPEN;
    }

    private List<String> strings(JsonNode value) {
        if (!value.isArray()) return List.of();
        return java.util.stream.StreamSupport.stream(value.spliterator(), false)
                .map(JsonNode::asText).filter(text -> !text.isBlank()).toList();
    }

    private boolean canFollowUp(TurnWork work, AnswerEvaluationDecision decision) {
        if (!decision.followUp()) return false;
        return decision.adaptive()
                ? work.followUpCount() < MAX_ADAPTIVE_FOLLOW_UPS
                : work.followUpCount() == 0;
    }

    private String streamFollowUp(TurnWork work,
                                  AnswerEvaluationDecision decision, Consumer<String> onDelta) {
        StringBuilder streamed = new StringBuilder();
        try {
            String context = objectMapper.writeValueAsString(Map.of(
                    "question", work.questionText(),
                    "expectedPoints", work.expectedPoints(),
                    "evaluationGuide", work.evaluationGuide(),
                    "candidateAnswer", work.answer(),
                    "evaluation", decision.evaluation(),
                    "interviewMemory", memoryService.get(
                            work.userId(), work.resumeId(), work.jobId()).promptContext()
            ));
            String traceId = "interview_followup_" + work.sessionId() + "_"
                    + UUID.randomUUID().toString().replace("-", "");
            var response = llmClient.stream(LlmRequest.secured(
                    PromptCatalog.INTERVIEW_FOLLOW_UP.systemPrompt(),
                    PromptCatalog.INTERVIEW_FOLLOW_UP.instruction(),
                    List.of(context), traceId, false
            ), delta -> {
                streamed.append(delta);
                onDelta.accept(delta);
            });
            return response == null || response.content() == null ? "" : response.content().trim();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize interview follow-up context", exception);
        } catch (RuntimeException exception) {
            log.warn("Interview follow-up stream failed; using validated fallback: sessionId={}, reason={}",
                    work.sessionId(), exception.getMessage());
            if (!streamed.isEmpty()) {
                return streamed.toString().trim();
            }
            String fallback = decision.followUpQuestion();
            if (!fallback.isBlank()) onDelta.accept(fallback);
            return fallback;
        }
    }

    private TurnWork beginTurn(Long userId, Long sessionId, String answer) {
        String normalized = answer == null ? "" : answer.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("INTERVIEW_ANSWER_REQUIRED", "回答不能为空", HttpStatus.BAD_REQUEST);
        }
        return Objects.requireNonNull(transactions.execute(status -> {
            InterviewSession session = requireSessionForUpdate(userId, sessionId);
            requireInProgress(session);
            if (session.isProcessingAnswer() && !session.processingIsStale(Instant.now().minus(Duration.ofMinutes(3)))) {
                throw new BusinessException("INTERVIEW_ANSWER_PROCESSING", "当前回答仍在处理中", HttpStatus.CONFLICT);
            }
            if (session.isProcessingAnswer()) discardStaleTurn(session);
            session.beginAnswerProcessing();
            InterviewQuestion question = requireQuestion(userId, session.currentQuestionId());
            InterviewMessage candidate = addMessage(
                    session, question.getId(), InterviewMessageRole.CANDIDATE, normalized);
            session.attachProcessingMessage(candidate.getId());
            return new TurnWork(session.getId(), session.getUserId(), session.getResumeId(), session.getJobId(),
                    question.getId(), question.getQuestionText(), question.getExpectedPoints(),
                    evaluationGuide(question), candidate.getId(), session.getFollowUpCount(), normalized);
        }));
    }

    private Map<String, Object> evaluationGuide(InterviewQuestion question) {
        Map<String, Object> guide = new java.util.LinkedHashMap<>();
        guide.put("answerOutline", question.getAnswerOutline());
        if (question.getReferenceAnswer() != null && !question.getReferenceAnswer().isBlank()) {
            guide.put("referenceAnswer", question.getReferenceAnswer());
        }
        guide.put("scoringRubric", question.getScoringRubric());
        guide.put("commonMistakes", question.getCommonMistakes());
        guide.put("followUpCandidates", question.getFollowUpCandidates());
        return Map.copyOf(guide);
    }

    private InterviewSessionResponse completeTurn(TurnWork work, AnswerEvaluationDecision decision, String followUp) {
        return Objects.requireNonNull(transactions.execute(status -> {
            InterviewSession session = requireSessionForUpdate(work.userId(), work.sessionId());
            requireInProgress(session);
            if (!session.isProcessingAnswer() || !session.currentQuestionId().equals(work.questionId())
                    || !work.candidateMessageId().equals(session.getProcessingMessageId())) {
                throw new BusinessException("INTERVIEW_TURN_CHANGED", "面试题状态已经变化", HttpStatus.CONFLICT);
            }
            InterviewQuestion question = requireQuestion(work.userId(), work.questionId());
            if (decision.overallScore() != null) {
                evaluationRepository.save(new InterviewAnswerEvaluation(
                        work.userId(), work.sessionId(), work.questionId(), work.candidateMessageId(),
                        decision.overallScore(), decision.evaluation()));
                if (decision.countsTowardMemory()) {
                    memoryService.record(session, question, decision.overallScore(), decision.evaluation());
                }
            }
            if (canFollowUp(work, decision) && followUp != null && !followUp.isBlank()) {
                session.recordFollowUp();
                addMessage(session, question.getId(), InterviewMessageRole.INTERVIEWER, followUp.trim());
            } else {
                moveNextOrFinish(session);
            }
            session.endAnswerProcessing();
            return response(session);
        }));
    }

    private void abortTurn(TurnWork work) {
        transactions.executeWithoutResult(status -> {
            InterviewSession session = requireSessionForUpdate(work.userId(), work.sessionId());
            if (session.isProcessingAnswer() && session.currentQuestionId().equals(work.questionId())
                    && work.candidateMessageId().equals(session.getProcessingMessageId())) {
                evaluationRepository.findByAnswerMessageId(work.candidateMessageId()).ifPresentOrElse(
                        ignored -> { }, () -> messageRepository.deleteById(work.candidateMessageId()));
                session.endAnswerProcessing();
            }
        });
    }

    private void discardStaleTurn(InterviewSession session) {
        Long messageId = session.getProcessingMessageId();
        if (messageId != null) {
            evaluationRepository.findByAnswerMessageId(messageId).ifPresentOrElse(
                    ignored -> { }, () -> messageRepository.deleteById(messageId));
        }
        session.endAnswerProcessing();
    }

    @SuppressWarnings("unchecked")
    private int canonicalScore(JsonNode validated, Map<String, Object> result) {
        Map<String, Integer> scores = new java.util.HashMap<>();
        for (JsonNode dimension : validated.path("dimensions")) {
            scores.put(dimension.path("key").asText(), dimension.path("score").asInt());
        }
        if (!scores.keySet().equals(SCORE_WEIGHTS.keySet())) {
            throw new IllegalArgumentException("Evaluation must contain every scoring dimension exactly once");
        }
        Object rawDimensions = result.get("dimensions");
        if (rawDimensions instanceof List<?> dimensions) {
            for (Object rawDimension : dimensions) {
                if (rawDimension instanceof Map<?, ?> dimension) {
                    Map<String, Object> mutable = (Map<String, Object>) dimension;
                    mutable.put("weight", SCORE_WEIGHTS.get(String.valueOf(mutable.get("key"))));
                }
            }
        }
        double weighted = SCORE_WEIGHTS.entrySet().stream()
                .mapToDouble(entry -> scores.get(entry.getKey()) * entry.getValue() / 100.0)
                .sum();
        return (int) Math.round(weighted);
    }

    private void moveNextOrFinish(InterviewSession session) {
        if (session.hasNextQuestion()) {
            session.advance();
            InterviewQuestion next = requireQuestion(session.getUserId(), session.currentQuestionId());
            addMessage(session, next.getId(), InterviewMessageRole.INTERVIEWER, next.getQuestionText());
        } else {
            session.finish();
            addMessage(session, session.currentQuestionId(), InterviewMessageRole.INTERVIEWER, CLOSING_MESSAGE);
        }
    }

    private InterviewMessage addMessage(InterviewSession session, Long questionId,
                                        InterviewMessageRole role, String content) {
        int nextSequence = messageRepository.maxSequence(session.getId()) + 1;
        return messageRepository.save(new InterviewMessage(
                session.getUserId(), session.getId(), questionId, role, content, nextSequence
        ));
    }

    private InterviewSession requireSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException("INTERVIEW_SESSION_NOT_FOUND",
                        "模拟面试会话不存在", HttpStatus.NOT_FOUND));
    }

    private InterviewSession requireSessionForUpdate(Long userId, Long sessionId) {
        return sessionRepository.findLockedByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException("INTERVIEW_SESSION_NOT_FOUND",
                        "模拟面试会话不存在", HttpStatus.NOT_FOUND));
    }

    private InterviewQuestion requireQuestion(Long userId, Long questionId) {
        return questionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new BusinessException("INTERVIEW_QUESTION_NOT_FOUND",
                        "面试题不存在", HttpStatus.NOT_FOUND));
    }

    private void requireResources(Long userId, Long resumeId, Long jobId) {
        if (resumeRepository.findByIdAndUserId(resumeId, userId).isEmpty()) {
            throw new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND);
        }
        if (jobRepository.findByIdAndUserId(jobId, userId).isEmpty()) {
            throw new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND);
        }
    }

    private void requireInProgress(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new BusinessException("INTERVIEW_SESSION_FINISHED", "本轮模拟面试已经结束", HttpStatus.CONFLICT);
        }
    }

    private InterviewSessionResponse response(InterviewSession session) {
        List<InterviewMessageResponse> messages = messageRepository
                .findByUserIdAndSessionIdOrderBySequenceNoAsc(session.getUserId(), session.getId()).stream()
                .map(InterviewMessageResponse::from)
                .toList();
        List<InterviewAnswerEvaluationResponse> evaluations = evaluationRepository
                .findByUserIdAndSessionIdOrderByCreatedAtAscIdAsc(session.getUserId(), session.getId()).stream()
                .map(InterviewAnswerEvaluationResponse::from)
                .toList();
        return new InterviewSessionResponse(
                session.getId(), session.getResumeId(), session.getJobId(), session.getStatus(),
                session.getCurrentQuestionIndex() + 1, session.getQuestionIds().size(), messages, evaluations,
                session.getCreatedAt(), session.getUpdatedAt(), session.getFinishedAt()
        );
    }

    private record TurnWork(Long sessionId, Long userId, Long resumeId, Long jobId, Long questionId,
                            String questionText, List<String> expectedPoints,
                            Map<String, Object> evaluationGuide, Long candidateMessageId,
                            int followUpCount, String answer) { }

    private record AnswerEvaluationDecision(boolean followUp, String followUpQuestion,
                                            Map<String, Object> evaluation, Integer overallScore,
                                            InterviewAnswerDisposition disposition,
                                            InterviewNextAction nextAction, boolean adaptive) {
        AnswerEvaluationDecision {
            followUpQuestion = followUp && followUpQuestion != null ? followUpQuestion.trim() : "";
            evaluation = evaluation == null ? Map.of() : Map.copyOf(evaluation);
        }

        boolean countsTowardMemory() {
            return disposition != InterviewAnswerDisposition.NO_ANSWER
                    && disposition != InterviewAnswerDisposition.OFF_TOPIC;
        }
    }

    public record InterviewMessageResponse(Long messageId, Long questionId, InterviewMessageRole role,
                                           String content, int sequenceNo, Instant createdAt) {
        static InterviewMessageResponse from(InterviewMessage message) {
            return new InterviewMessageResponse(message.getId(), message.getQuestionId(), message.getRole(),
                    message.getContent(), message.getSequenceNo(), message.getCreatedAt());
        }
    }

    public record InterviewSessionResponse(Long sessionId, Long resumeId, Long jobId,
                                           InterviewSessionStatus status, int currentQuestion,
                                           int totalQuestions, List<InterviewMessageResponse> messages,
                                           List<InterviewAnswerEvaluationResponse> evaluations,
                                           Instant createdAt, Instant updatedAt, Instant finishedAt) {
    }

    public record InterviewAnswerEvaluationResponse(Long evaluationId, Long questionId, Long answerMessageId,
                                                    int overallScore, Map<String, Object> result,
                                                    String schemaVersion, Instant createdAt) {
        static InterviewAnswerEvaluationResponse from(InterviewAnswerEvaluation evaluation) {
            return new InterviewAnswerEvaluationResponse(
                    evaluation.getId(), evaluation.getQuestionId(), evaluation.getAnswerMessageId(),
                    evaluation.getOverallScore(), evaluation.getResultJson(), evaluation.getSchemaVersion(),
                    evaluation.getCreatedAt()
            );
        }
    }

    public record InterviewSessionSummary(Long sessionId, Long resumeId, Long jobId,
                                          InterviewSessionStatus status, int currentQuestion,
                                          int totalQuestions, Instant createdAt, Instant updatedAt,
                                          Instant finishedAt) {
        static InterviewSessionSummary from(InterviewSession session) {
            return new InterviewSessionSummary(session.getId(), session.getResumeId(), session.getJobId(),
                    session.getStatus(), session.getCurrentQuestionIndex() + 1, session.getQuestionIds().size(),
                    session.getCreatedAt(), session.getUpdatedAt(), session.getFinishedAt());
        }
    }
}
