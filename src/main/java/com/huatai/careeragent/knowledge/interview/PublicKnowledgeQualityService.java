package com.huatai.careeragent.knowledge.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.PromptCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PublicKnowledgeQualityService {
    private final KnowledgeSourceRepository sourceRepository;
    private final InterviewExperienceRepository experienceRepository;
    private final PublicInterviewQuestionRepository questionRepository;
    private final PublicQuestionEvidenceRepository evidenceRepository;
    private final LlmClient llmClient;
    private final SchemaRepairService schemaRepair;
    private final ObjectMapper objectMapper;

    public PublicKnowledgeQualityService(KnowledgeSourceRepository sourceRepository,
                                         InterviewExperienceRepository experienceRepository,
                                         PublicInterviewQuestionRepository questionRepository,
                                         PublicQuestionEvidenceRepository evidenceRepository,
                                         LlmClient llmClient, SchemaRepairService schemaRepair,
                                         ObjectMapper objectMapper) {
        this.sourceRepository = sourceRepository;
        this.experienceRepository = experienceRepository;
        this.questionRepository = questionRepository;
        this.evidenceRepository = evidenceRepository;
        this.llmClient = llmClient;
        this.schemaRepair = schemaRepair;
        this.objectMapper = objectMapper;
    }

    public QualityReviewResponse review(Long sourceId) {
        KnowledgeSource source = sourceRepository.findById(sourceId).orElseThrow(() ->
                new BusinessException("PUBLIC_KNOWLEDGE_SOURCE_NOT_FOUND", "公共知识来源不存在", HttpStatus.NOT_FOUND));
        if (source.getReviewStatus() != KnowledgeSource.ReviewStatus.PENDING) {
            throw new BusinessException("PUBLIC_KNOWLEDGE_ALREADY_REVIEWED", "已审核来源不能重新执行质量审查",
                    HttpStatus.CONFLICT);
        }
        List<QuestionQualityResult> results = new ArrayList<>();
        for (InterviewExperience experience : experienceRepository.findBySourceIdOrderByIdAsc(sourceId)) {
            for (PublicInterviewQuestion question : questionRepository.findByExperienceIdOrderByIdAsc(experience.getId())) {
                results.add(reviewOne(source, experience, question));
            }
        }
        long accepted = results.stream().filter(item -> item.status() == PublicInterviewQuestion.QualityStatus.ACCEPTED).count();
        long rejected = results.stream().filter(item -> item.status() == PublicInterviewQuestion.QualityStatus.REJECTED).count();
        return new QualityReviewResponse(sourceId, results.size(), (int) accepted,
                results.size() - (int) accepted - (int) rejected, (int) rejected, List.copyOf(results));
    }

    private QuestionQualityResult reviewOne(KnowledgeSource source, InterviewExperience experience,
                                            PublicInterviewQuestion question) {
        String traceId = "public_quality_" + source.getId() + "_" + question.getId();
        String context = json(Map.of(
                "source", sourceContext(source),
                "experience", experienceContext(experience),
                "question", questionContext(question),
                "independentSourceCount", evidenceRepository.sourceCount(question.getQuestionHash())
        ));
        var response = llmClient.complete(LlmRequest.secured(
                PromptCatalog.PUBLIC_KNOWLEDGE_QUALITY_REVIEW.systemPrompt(),
                PromptCatalog.PUBLIC_KNOWLEDGE_QUALITY_REVIEW.instruction(),
                List.of(context), traceId, true));
        var validated = schemaRepair.validateOrRepair("public_interview_quality_review.schema.json",
                response.content(), traceId);
        JsonNode value = validated.value();
        int technical = value.path("technicalAccuracy").asInt();
        int plausible = value.path("interviewPlausibility").asInt();
        int currency = value.path("currency").asInt();
        int answer = value.path("answerQuality").asInt();
        int score = (int) Math.round(technical * 0.35 + plausible * 0.25 + currency * 0.15 + answer * 0.25);
        String decision = value.path("decision").asText();
        PublicInterviewQuestion.QualityStatus status = qualityStatus(decision, score, technical, plausible,
                currency, answer);
        int evidenceCount = evidenceRepository.sourceCount(question.getQuestionHash());
        PublicInterviewQuestion.ConfidenceLabel confidence = confidence(source, status, currency, evidenceCount);
        Map<String, Object> audit = objectMapper.convertValue(value, new TypeReference<>() { });
        Map<String, Object> canonical = new LinkedHashMap<>(audit);
        canonical.values().removeIf(Objects::isNull);
        canonical.put("serverQualityScore", score);
        canonical.put("independentSourceCount", evidenceCount);
        canonical.put("confidenceLabel", confidence.name());
        question.applyQualityReview(status, score, confidence, Map.copyOf(canonical));
        questionRepository.save(question);
        return new QuestionQualityResult(question.getId(), status, score, confidence,
                value.path("reviewSummary").asText(), strings(value.path("issues")));
    }

    private PublicInterviewQuestion.QualityStatus qualityStatus(String decision, int score, int technical,
                                                                 int plausible, int currency, int answer) {
        if ("REJECT".equals(decision) || technical < 50 || plausible < 50) {
            return PublicInterviewQuestion.QualityStatus.REJECTED;
        }
        if ("ACCEPT".equals(decision) && score >= PublicKnowledgeAdminService.MINIMUM_PUBLISH_QUALITY
                && technical >= 75 && plausible >= 70 && currency >= 60 && answer >= 70) {
            return PublicInterviewQuestion.QualityStatus.ACCEPTED;
        }
        return PublicInterviewQuestion.QualityStatus.NEEDS_REVIEW;
    }

    private PublicInterviewQuestion.ConfidenceLabel confidence(KnowledgeSource source,
            PublicInterviewQuestion.QualityStatus status, int currency, int evidenceCount) {
        if (status == PublicInterviewQuestion.QualityStatus.REJECTED)
            return PublicInterviewQuestion.ConfidenceLabel.REJECTED;
        if (currency < 60) return PublicInterviewQuestion.ConfidenceLabel.OUTDATED;
        if (evidenceCount >= 2) return PublicInterviewQuestion.ConfidenceLabel.MULTI_SOURCE_VERIFIED;
        if (source.getSourceType() == KnowledgeSource.SourceType.MANUAL)
            return PublicInterviewQuestion.ConfidenceLabel.AI_DERIVED;
        return PublicInterviewQuestion.ConfidenceLabel.SINGLE_SOURCE;
    }

    private Map<String, Object> sourceContext(KnowledgeSource source) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sourceType", source.getSourceType().name());
        value.put("platform", source.getPlatform());
        value.put("title", source.getTitle());
        value.put("publishedAt", source.getPublishedAt());
        value.values().removeIf(Objects::isNull);
        return Map.copyOf(value);
    }
    private Map<String, Object> experienceContext(InterviewExperience experience) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("industry", experience.getIndustry()); value.put("company", experience.getCompany());
        value.put("position", experience.getPosition()); value.put("experienceLevel", experience.getExperienceLevel());
        value.put("interviewRound", experience.getInterviewRound()); value.put("summary", experience.getSummary());
        value.put("eventDate", experience.getEventDate()); value.values().removeIf(Objects::isNull);
        return Map.copyOf(value);
    }
    private Map<String, Object> questionContext(PublicInterviewQuestion question) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("question", question.getNormalizedQuestion()); value.put("questionType", question.getQuestionType());
        value.put("difficulty", question.getDifficulty()); value.put("knowledgePoints", question.getKnowledgePoints());
        value.put("answerOutline", question.getAnswerOutline());
        if (question.getReferenceAnswer() != null) value.put("referenceAnswer", question.getReferenceAnswer());
        value.put("scoringRubric", question.getScoringRubric()); value.put("commonMistakes", question.getCommonMistakes());
        value.put("followUpCandidates", question.getFollowUpCandidates()); return Map.copyOf(value);
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize quality review context", exception); }
    }
    private List<String> strings(JsonNode array) {
        if (!array.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }

    public record QualityReviewResponse(Long sourceId, int total, int accepted, int needsReview, int rejected,
                                        List<QuestionQualityResult> questions) { }
    public record QuestionQualityResult(Long questionId, PublicInterviewQuestion.QualityStatus status,
                                        int qualityScore, PublicInterviewQuestion.ConfidenceLabel confidenceLabel,
                                        String summary, List<String> issues) { }
}
