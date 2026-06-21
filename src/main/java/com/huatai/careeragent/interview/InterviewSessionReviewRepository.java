package com.huatai.careeragent.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewSessionReviewRepository extends JpaRepository<InterviewSessionReview, Long> {
    Optional<InterviewSessionReview> findByUserIdAndSessionId(Long userId, Long sessionId);
}
