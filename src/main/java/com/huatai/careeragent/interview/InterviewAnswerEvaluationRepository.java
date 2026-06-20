package com.huatai.careeragent.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewAnswerEvaluationRepository extends JpaRepository<InterviewAnswerEvaluation, Long> {
    List<InterviewAnswerEvaluation> findByUserIdAndSessionIdOrderByCreatedAtAscIdAsc(Long userId, Long sessionId);
}
