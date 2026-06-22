package com.huatai.careeragent.knowledge.interview;

import java.time.Instant;
import java.time.LocalDate;

record PublicKnowledgeSearchRow(
        Long questionId, String questionHash, String question, String questionType, String difficulty,
        int questionQualityScore, String confidenceLabel,
        String knowledgePoints, String answerOutline, String referenceAnswer, String scoringRubric,
        String commonMistakes, String followUpCandidates, String industry, String company,
        String position, String experienceLevel, String interviewRound, LocalDate eventDate,
        String sourceTitle, String sourceUrl, String platform, Instant collectedAt,
        double sourceQualityScore, double score
) { }
