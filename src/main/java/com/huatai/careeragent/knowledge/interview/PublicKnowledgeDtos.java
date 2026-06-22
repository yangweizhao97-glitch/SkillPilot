package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.interview.QuestionDifficulty;
import com.huatai.careeragent.interview.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PublicKnowledgeDtos {
    private PublicKnowledgeDtos() { }

    public record CreateSourceRequest(
            @NotNull KnowledgeSource.SourceType sourceType,
            @Size(max = 64) String platform,
            @Size(max = 1000) String sourceUrl,
            @NotBlank @Size(max = 255) String title,
            Instant publishedAt,
            @NotNull KnowledgeSource.CopyrightStatus copyrightStatus,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal qualityScore,
            @NotEmpty List<@Valid ExperienceInput> experiences
    ) { }

    public record ExperienceInput(
            @Size(max = 120) String industry,
            @Size(max = 255) String company,
            @NotBlank @Size(max = 255) String position,
            @Size(max = 120) String experienceLevel,
            @Size(max = 120) String interviewRound,
            @NotBlank @Size(max = 4000) String summary,
            List<@Size(max = 80) String> tags,
            LocalDate eventDate,
            @NotEmpty List<@Valid QuestionInput> questions
    ) { }

    public record QuestionInput(
            @NotBlank @Size(max = 2000) String question,
            @NotNull QuestionType questionType,
            @NotNull QuestionDifficulty difficulty,
            List<@Size(max = 300) String> knowledgePoints,
            List<@Size(max = 500) String> answerOutline,
            @Size(max = 8000) String referenceAnswer,
            List<Map<String, Object>> scoringRubric,
            List<@Size(max = 500) String> commonMistakes,
            List<@Size(max = 1000) String> followUpCandidates
    ) { }

    public record ReviewRequest(@NotNull KnowledgeSource.ReviewStatus decision,
                                @Size(max = 500) String reason) { }

    public record SourceResponse(Long sourceId, String title, String platform, String sourceUrl,
                                 KnowledgeSource.ReviewStatus reviewStatus, BigDecimal qualityScore,
                                 int experienceCount, int questionCount, int acceptedQuestions,
                                 int needsReviewQuestions, int rejectedQuestions, Instant collectedAt,
                                 String rejectionReason) { }
}
