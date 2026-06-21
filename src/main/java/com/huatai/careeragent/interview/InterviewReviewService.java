package com.huatai.careeragent.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.PromptCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InterviewReviewService {
    private static final Logger log = LoggerFactory.getLogger(InterviewReviewService.class);
    private static final List<String> DIMENSION_KEYS = List.of("accuracy", "relevance", "depth", "communication");
    private static final Map<String, String> DIMENSION_LABELS = Map.of(
            "accuracy", "准确性", "relevance", "相关性", "depth", "深度", "communication", "表达"
    );

    private final InterviewSessionRepository sessionRepository;
    private final InterviewSessionReviewRepository reviewRepository;
    private final InterviewAnswerEvaluationRepository evaluationRepository;
    private final InterviewMessageRepository messageRepository;
    private final LlmClient llmClient;
    private final SchemaRepairService schemaRepairService;
    private final ObjectMapper objectMapper;

    public InterviewReviewService(InterviewSessionRepository sessionRepository,
                                  InterviewSessionReviewRepository reviewRepository,
                                  InterviewAnswerEvaluationRepository evaluationRepository,
                                  InterviewMessageRepository messageRepository, LlmClient llmClient,
                                  SchemaRepairService schemaRepairService, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.reviewRepository = reviewRepository;
        this.evaluationRepository = evaluationRepository;
        this.messageRepository = messageRepository;
        this.llmClient = llmClient;
        this.schemaRepairService = schemaRepairService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ReviewState get(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        return reviewRepository.findByUserIdAndSessionId(userId, sessionId)
                .map(review -> new ReviewState(true, ReviewResponse.from(review)))
                .orElseGet(() -> new ReviewState(false, null));
    }

    @Transactional
    public ReviewResponse generate(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findLockedByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> notFound());
        if (session.getStatus() != InterviewSessionStatus.FINISHED) {
            throw new BusinessException("INTERVIEW_NOT_FINISHED", "请先结束本轮模拟面试", HttpStatus.CONFLICT);
        }
        var existing = reviewRepository.findByUserIdAndSessionId(userId, sessionId);
        if (existing.isPresent()) return ReviewResponse.from(existing.get());

        List<InterviewAnswerEvaluation> evaluations = evaluationRepository
                .findByUserIdAndSessionIdOrderByCreatedAtAscIdAsc(userId, sessionId).stream()
                .filter(this::countsTowardReview)
                .toList();
        if (evaluations.isEmpty()) {
            throw new BusinessException("INTERVIEW_EVALUATIONS_REQUIRED",
                    "当前会话没有可用于复盘的回答评分", HttpStatus.CONFLICT);
        }

        Map<String, Integer> dimensionScores = dimensionAverages(evaluations);
        int overallScore = (int) Math.round(evaluations.stream()
                .mapToInt(InterviewAnswerEvaluation::getOverallScore).average().orElse(0));
        GeneratedReview generated = generateResult(userId, sessionId, evaluations, dimensionScores, overallScore);
        InterviewSessionReview saved = reviewRepository.save(new InterviewSessionReview(
                userId, sessionId, overallScore, evaluations.size(), generated.result(), generated.source()
        ));
        return ReviewResponse.from(saved);
    }

    private GeneratedReview generateResult(Long userId, Long sessionId,
                                            List<InterviewAnswerEvaluation> evaluations,
                                            Map<String, Integer> dimensionScores, int overallScore) {
        try {
            String context = objectMapper.writeValueAsString(Map.of(
                    "transcript", messageRepository.findByUserIdAndSessionIdOrderBySequenceNoAsc(userId, sessionId)
                            .stream().map(message -> Map.of(
                                    "role", message.getRole().name(), "content", message.getContent()
                            )).toList(),
                    "evaluations", evaluations.stream().map(InterviewAnswerEvaluation::getResultJson).toList(),
                    "serverMetrics", Map.of("overallScore", overallScore, "dimensions", dimensionScores)
            ));
            String traceId = "interview_review_" + sessionId + "_"
                    + UUID.randomUUID().toString().replace("-", "");
            var response = llmClient.complete(LlmRequest.secured(
                    PromptCatalog.SESSION_REVIEW.systemPrompt(),
                    PromptCatalog.SESSION_REVIEW.instruction(),
                    List.of(context), traceId, true
            ));
            JsonNode validated = schemaRepairService.validateOrRepair(
                    "interview_session_review.schema.json", response.content(), traceId
            ).value();
            Map<String, Object> result = objectMapper.convertValue(validated, new TypeReference<>() { });
            canonicalizeScores(result, dimensionScores, overallScore);
            return new GeneratedReview(result, "LLM");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize interview review context", exception);
        } catch (RuntimeException exception) {
            log.warn("Interview review generation failed; using deterministic fallback: sessionId={}, reason={}",
                    sessionId, exception.getMessage());
            return new GeneratedReview(fallback(evaluations, dimensionScores, overallScore), "FALLBACK");
        }
    }

    @SuppressWarnings("unchecked")
    private void canonicalizeScores(Map<String, Object> result, Map<String, Integer> scores, int overallScore) {
        result.put("overallScore", overallScore);
        Map<String, Map<String, Object>> generated = new LinkedHashMap<>();
        Object rawDimensions = result.get("dimensions");
        if (rawDimensions instanceof List<?> dimensions) {
            for (Object item : dimensions) {
                if (item instanceof Map<?, ?> map) {
                    generated.put(String.valueOf(map.get("key")), (Map<String, Object>) map);
                }
            }
        }
        List<Map<String, Object>> canonical = DIMENSION_KEYS.stream().map(key -> {
            Map<String, Object> item = generated.getOrDefault(key, Map.of());
            return Map.<String, Object>of(
                    "key", key,
                    "label", DIMENSION_LABELS.get(key),
                    "score", scores.get(key),
                    "assessment", String.valueOf(item.getOrDefault("assessment", "基于逐题评分计算。"))
            );
        }).toList();
        result.put("dimensions", canonical);
    }

    private Map<String, Integer> dimensionAverages(List<InterviewAnswerEvaluation> evaluations) {
        Map<String, List<Integer>> grouped = DIMENSION_KEYS.stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> new ArrayList<>(), (a, b) -> a,
                        LinkedHashMap::new));
        for (InterviewAnswerEvaluation evaluation : evaluations) {
            Object raw = evaluation.getResultJson().get("dimensions");
            if (!(raw instanceof List<?> dimensions)) continue;
            for (Object item : dimensions) {
                if (!(item instanceof Map<?, ?> dimension)) continue;
                String key = String.valueOf(dimension.get("key"));
                Object score = dimension.get("score");
                if (grouped.containsKey(key) && score instanceof Number number) {
                    grouped.get(key).add(number.intValue());
                }
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        grouped.forEach((key, values) -> result.put(key, values.isEmpty() ? 0
                : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0))));
        return result;
    }

    private Map<String, Object> fallback(List<InterviewAnswerEvaluation> evaluations,
                                         Map<String, Integer> scores, int overallScore) {
        List<String> strengths = collectStrings(evaluations, "strengths");
        List<String> gaps = collectStrings(evaluations, "improvements");
        if (strengths.isEmpty()) strengths = List.of("已完成本轮面试并形成可复盘的回答记录");
        if (gaps.isEmpty()) gaps = List.of("继续补充答案中的事实依据和具体案例");
        String weakest = scores.entrySet().stream().min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("depth");
        List<Map<String, Object>> dimensions = DIMENSION_KEYS.stream().map(key -> Map.<String, Object>of(
                "key", key, "label", DIMENSION_LABELS.get(key), "score", scores.get(key),
                "assessment", key.equals(weakest) ? "这是当前最需要优先提升的维度。" : "基于逐题评分汇总。"
        )).toList();
        List<Map<String, Object>> actions = new ArrayList<>();
        for (int i = 0; i < Math.min(gaps.size(), 3); i++) {
            actions.add(Map.of("priority", i + 1, "action", "针对该缺口重写并口述一次答案：" + gaps.get(i),
                    "reason", "该问题在逐题评分中重复出现或影响关键得分。"));
        }
        return new LinkedHashMap<>(Map.of(
                "schemaVersion", "1.0",
                "overallScore", overallScore,
                "summary", "本轮共完成 " + evaluations.size() + " 次回答评估。建议优先修复最低分维度，再进行限时复述练习。",
                "dimensions", dimensions,
                "strengths", strengths.stream().limit(5).toList(),
                "gaps", gaps.stream().limit(5).toList(),
                "actionPlan", actions,
                "recommendedPracticeQuestions", List.of("请用一个真实项目案例重新回答本轮得分最低的问题。")
        ));
    }

    private List<String> collectStrings(List<InterviewAnswerEvaluation> evaluations, String key) {
        return evaluations.stream().flatMap(evaluation -> {
                    Object value = evaluation.getResultJson().get(key);
                    return value instanceof List<?> list ? list.stream() : java.util.stream.Stream.empty();
                }).map(String::valueOf).filter(value -> !value.isBlank()).distinct().limit(5).toList();
    }

    private boolean countsTowardReview(InterviewAnswerEvaluation evaluation) {
        Object disposition = evaluation.getResultJson().get("answerDisposition");
        return !InterviewAnswerDisposition.NO_ANSWER.name().equals(disposition)
                && !InterviewAnswerDisposition.OFF_TOPIC.name().equals(disposition);
    }

    private InterviewSession requireSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId).orElseThrow(this::notFound);
    }

    private BusinessException notFound() {
        return new BusinessException("INTERVIEW_SESSION_NOT_FOUND", "模拟面试会话不存在", HttpStatus.NOT_FOUND);
    }

    private record GeneratedReview(Map<String, Object> result, String source) { }

    public record ReviewState(boolean available, ReviewResponse review) { }

    public record ReviewResponse(Long reviewId, Long sessionId, int overallScore, int evaluatedAnswers,
                                 Map<String, Object> result, String schemaVersion, String generationSource,
                                 Instant createdAt) {
        static ReviewResponse from(InterviewSessionReview review) {
            return new ReviewResponse(review.getId(), review.getSessionId(), review.getOverallScore(),
                    review.getEvaluatedAnswers(), review.getResultJson(), review.getSchemaVersion(),
                    review.getGenerationSource(), review.getCreatedAt());
        }
    }
}
