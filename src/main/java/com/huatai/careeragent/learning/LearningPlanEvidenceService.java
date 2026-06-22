package com.huatai.careeragent.learning;

import com.huatai.careeragent.interview.InterviewAnswerEvaluationRepository;
import com.huatai.careeragent.interview.InterviewSessionRepository;
import com.huatai.careeragent.interview.InterviewSessionReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LearningPlanEvidenceService {
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerEvaluationRepository evaluationRepository;
    private final InterviewSessionReviewRepository reviewRepository;

    public LearningPlanEvidenceService(InterviewSessionRepository sessionRepository,
                                       InterviewAnswerEvaluationRepository evaluationRepository,
                                       InterviewSessionReviewRepository reviewRepository) {
        this.sessionRepository = sessionRepository;
        this.evaluationRepository = evaluationRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> collect(Long userId, Long resumeId, Long jobId) {
        if (resumeId == null || jobId == null) return Map.of("sessions", List.of());
        List<Map<String, Object>> sessions = new ArrayList<>();
        sessionRepository.findTop3ByUserIdAndResumeIdAndJobIdOrderByUpdatedAtDescIdDesc(
                userId, resumeId, jobId).forEach(session -> {
            List<Map<String, Object>> evaluations = evaluationRepository
                    .findByUserIdAndSessionIdOrderByCreatedAtAscIdAsc(userId, session.getId()).stream()
                    .map(evaluation -> Map.<String, Object>of(
                            "questionId", evaluation.getQuestionId(),
                            "overallScore", evaluation.getOverallScore(),
                            "result", evaluation.getResultJson()
                    )).toList();
            Map<String, Object> value = new java.util.LinkedHashMap<>();
            value.put("sessionId", session.getId());
            value.put("status", session.getStatus().name());
            value.put("evaluations", evaluations);
            reviewRepository.findByUserIdAndSessionId(userId, session.getId())
                    .ifPresent(review -> value.put("review", review.getResultJson()));
            sessions.add(Map.copyOf(value));
        });
        return Map.of("sessions", List.copyOf(sessions));
    }
}
