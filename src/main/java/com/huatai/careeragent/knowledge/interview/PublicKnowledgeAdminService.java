package com.huatai.careeragent.knowledge.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.knowledge.embedding.EmbeddingClient;
import com.huatai.careeragent.knowledge.embedding.EmbeddingVectorFormatter;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class PublicKnowledgeAdminService {
    private final KnowledgeSourceRepository sourceRepository;
    private final InterviewExperienceRepository experienceRepository;
    private final PublicInterviewQuestionRepository questionRepository;
    private final PublicKnowledgeEmbeddingRepository embeddingRepository;
    private final PublicKnowledgeSanitizer sanitizer;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingVectorFormatter vectorFormatter;
    private final ObjectMapper objectMapper;

    public PublicKnowledgeAdminService(KnowledgeSourceRepository sourceRepository,
                                       InterviewExperienceRepository experienceRepository,
                                       PublicInterviewQuestionRepository questionRepository,
                                       PublicKnowledgeEmbeddingRepository embeddingRepository,
                                       PublicKnowledgeSanitizer sanitizer, EmbeddingClient embeddingClient,
                                       EmbeddingVectorFormatter vectorFormatter, ObjectMapper objectMapper) {
        this.sourceRepository = sourceRepository;
        this.experienceRepository = experienceRepository;
        this.questionRepository = questionRepository;
        this.embeddingRepository = embeddingRepository;
        this.sanitizer = sanitizer;
        this.embeddingClient = embeddingClient;
        this.vectorFormatter = vectorFormatter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SourceResponse create(Long adminId, CreateSourceRequest request) {
        String hash = hash(request);
        sourceRepository.findByContentHash(hash).ifPresent(existing -> {
            throw new BusinessException("PUBLIC_KNOWLEDGE_DUPLICATE", "该来源已经导入", HttpStatus.CONFLICT);
        });
        String trace = "public_knowledge_import_" + hash.substring(0, 12);
        KnowledgeSource source = sourceRepository.save(new KnowledgeSource(
                request.sourceType(), clean(request.platform(), trace), cleanUrl(request.sourceUrl()),
                requiredClean(request.title(), trace), request.publishedAt(), hash, request.copyrightStatus(),
                request.qualityScore() == null ? new BigDecimal("0.5000") : request.qualityScore(), adminId));
        for (ExperienceInput input : request.experiences()) {
            InterviewExperience experience = experienceRepository.save(new InterviewExperience(
                    source.getId(), clean(input.industry(), trace), clean(input.company(), trace),
                    requiredClean(input.position(), trace), clean(input.experienceLevel(), trace),
                    clean(input.interviewRound(), trace), requiredClean(input.summary(), trace),
                    cleanList(input.tags(), trace), input.eventDate()));
            java.util.Set<String> questionHashes = new java.util.HashSet<>();
            for (QuestionInput question : input.questions()) {
                String normalized = requiredClean(question.question(), trace);
                String questionHash = sha256(normalized.toLowerCase());
                if (!questionHashes.add(questionHash)) continue;
                questionRepository.save(new PublicInterviewQuestion(
                        experience.getId(), normalized, questionHash, question.questionType(),
                        question.difficulty(), cleanList(question.knowledgePoints(), trace),
                        cleanList(question.answerOutline(), trace), clean(question.referenceAnswer(), trace),
                        cleanRubric(question.scoringRubric(), trace), cleanList(question.commonMistakes(), trace),
                        cleanList(question.followUpCandidates(), trace)));
            }
        }
        return response(source);
    }

    @Transactional
    public SourceResponse process(Long sourceId) {
        KnowledgeSource source = require(sourceId);
        if (source.getReviewStatus() != KnowledgeSource.ReviewStatus.PENDING) {
            throw new BusinessException("PUBLIC_KNOWLEDGE_ALREADY_REVIEWED", "已审核来源不能重新处理", HttpStatus.CONFLICT);
        }
        for (InterviewExperience experience : experienceRepository.findBySourceIdOrderByIdAsc(sourceId)) {
            for (PublicInterviewQuestion question : questionRepository.findByExperienceIdOrderByIdAsc(experience.getId())) {
                var embedding = embeddingClient.embed(searchableText(experience, question));
                embeddingRepository.update(question.getId(), vectorFormatter.toPgVector(embedding.vector()));
            }
        }
        return response(source);
    }

    @Transactional
    public SourceResponse review(Long reviewerId, Long sourceId, ReviewRequest request) {
        KnowledgeSource source = require(sourceId);
        if (source.getReviewStatus() != KnowledgeSource.ReviewStatus.PENDING) {
            throw new BusinessException("PUBLIC_KNOWLEDGE_ALREADY_REVIEWED", "来源已经审核", HttpStatus.CONFLICT);
        }
        if (request.decision() == KnowledgeSource.ReviewStatus.PENDING) {
            throw new BusinessException("PUBLIC_KNOWLEDGE_INVALID_REVIEW", "审核结果必须是 APPROVED 或 REJECTED", HttpStatus.BAD_REQUEST);
        }
        List<InterviewExperience> experiences = experienceRepository.findBySourceIdOrderByIdAsc(sourceId);
        if (request.decision() == KnowledgeSource.ReviewStatus.APPROVED) {
            if (!embeddingRepository.allEmbedded(sourceId)) {
                throw new BusinessException("PUBLIC_KNOWLEDGE_NOT_PROCESSED", "来源尚未完成向量化", HttpStatus.CONFLICT);
            }
            source.approve(reviewerId);
            experiences.forEach(experience -> {
                experience.publish();
                questionRepository.findByExperienceIdOrderByIdAsc(experience.getId())
                        .forEach(PublicInterviewQuestion::publish);
            });
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new BusinessException("PUBLIC_KNOWLEDGE_REJECTION_REASON_REQUIRED", "拒绝时必须填写原因", HttpStatus.BAD_REQUEST);
            }
            source.reject(reviewerId, request.reason().trim());
            experiences.forEach(experience -> {
                experience.reject();
                questionRepository.findByExperienceIdOrderByIdAsc(experience.getId())
                        .forEach(PublicInterviewQuestion::reject);
            });
        }
        return response(source);
    }

    @Transactional(readOnly = true)
    public SourceResponse get(Long sourceId) { return response(require(sourceId)); }

    private KnowledgeSource require(Long sourceId) {
        return sourceRepository.findById(sourceId).orElseThrow(() ->
                new BusinessException("PUBLIC_KNOWLEDGE_SOURCE_NOT_FOUND", "公共知识来源不存在", HttpStatus.NOT_FOUND));
    }

    private SourceResponse response(KnowledgeSource source) {
        List<InterviewExperience> experiences = experienceRepository.findBySourceIdOrderByIdAsc(source.getId());
        int questions = experiences.stream().mapToInt(item ->
                questionRepository.findByExperienceIdOrderByIdAsc(item.getId()).size()).sum();
        return new SourceResponse(source.getId(), source.getTitle(), source.getPlatform(), source.getSourceUrl(),
                source.getReviewStatus(), source.getQualityScore(), experiences.size(), questions,
                source.getCollectedAt(), source.getRejectionReason());
    }

    private String searchableText(InterviewExperience experience, PublicInterviewQuestion question) {
        return String.join(" ", values(experience.getIndustry(), experience.getCompany(), experience.getPosition(),
                experience.getExperienceLevel(), experience.getInterviewRound(), experience.getSummary(),
                question.getNormalizedQuestion(), String.join(" ", question.getKnowledgePoints()),
                question.getReferenceAnswer()));
    }

    private List<String> values(String... values) {
        return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank()).toList();
    }

    private String clean(String value, String trace) { return sanitizer.sanitize(value, trace); }
    private String requiredClean(String value, String trace) {
        String cleaned = clean(value, trace);
        if (cleaned == null || cleaned.isBlank()) throw new BusinessException(
                "PUBLIC_KNOWLEDGE_EMPTY_AFTER_SANITIZE", "内容脱敏后为空", HttpStatus.BAD_REQUEST);
        return cleaned;
    }
    private String cleanUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) throw new BusinessException(
                "PUBLIC_KNOWLEDGE_INVALID_URL", "来源链接必须使用 HTTP 或 HTTPS", HttpStatus.BAD_REQUEST);
        return url;
    }
    private List<String> cleanList(List<String> values, String trace) {
        if (values == null) return List.of();
        return values.stream().map(value -> clean(value, trace)).filter(value -> value != null && !value.isBlank())
                .distinct().limit(30).toList();
    }
    private List<Map<String, Object>> cleanRubric(List<Map<String, Object>> rubric, String trace) {
        if (rubric == null) return List.of();
        return rubric.stream().limit(20).map(item -> {
            Map<String, Object> cleaned = new java.util.LinkedHashMap<>();
            item.forEach((key, value) -> {
                String safeKey = clean(key, trace);
                if (safeKey == null || safeKey.isBlank() || value == null) return;
                Object safeValue = value instanceof String text ? clean(text, trace) : value;
                if (safeValue instanceof Number || safeValue instanceof Boolean || safeValue instanceof String) {
                    cleaned.put(safeKey, safeValue);
                }
            });
            return Map.copyOf(cleaned);
        }).toList();
    }
    private String hash(Object value) {
        try { return sha256(objectMapper.writeValueAsString(value)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot hash public knowledge", exception); }
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
