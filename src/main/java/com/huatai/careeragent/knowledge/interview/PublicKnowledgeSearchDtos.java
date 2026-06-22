package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.interview.QuestionDifficulty;
import com.huatai.careeragent.interview.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PublicKnowledgeSearchDtos {
    private PublicKnowledgeSearchDtos() { }

    public record SearchRequest(
            @NotBlank @Size(max = 1000) String query,
            @Size(max = 120) String industry,
            @Size(max = 255) String position,
            @Size(max = 255) String company,
            @Size(max = 120) String experienceLevel,
            @Size(max = 120) String interviewRound,
            @Min(1) @Max(20) Integer topK
    ) { }

    public record SearchResponse(List<SearchItem> items) { }

    public record SearchItem(
            String citationId,
            Long questionId,
            String question,
            QuestionType questionType,
            QuestionDifficulty difficulty,
            List<String> knowledgePoints,
            List<String> answerOutline,
            String referenceAnswer,
            List<Map<String, Object>> scoringRubric,
            List<String> commonMistakes,
            List<String> followUpCandidates,
            String industry,
            String company,
            String position,
            String experienceLevel,
            String interviewRound,
            LocalDate eventDate,
            String sourceTitle,
            String sourceUrl,
            String platform,
            Instant collectedAt,
            int questionQualityScore,
            PublicInterviewQuestion.ConfidenceLabel confidenceLabel,
            double sourceQualityScore,
            double score
    ) { }
}
