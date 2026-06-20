package com.huatai.careeragent.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);
    List<InterviewSession> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
