package com.huatai.careeragent.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewAnswerEvaluationRepository extends JpaRepository<InterviewAnswerEvaluation, Long> {
    List<InterviewAnswerEvaluation> findByUserIdAndSessionIdOrderByCreatedAtAscIdAsc(Long userId, Long sessionId);
    Optional<InterviewAnswerEvaluation> findByAnswerMessageId(Long answerMessageId);
    Optional<InterviewAnswerEvaluation> findByIdAndUserId(Long id, Long userId);
}
