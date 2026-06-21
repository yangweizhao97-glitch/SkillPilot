package com.huatai.careeragent.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionMemoryRepository extends JpaRepository<InterviewSessionMemory, Long> {
    Optional<InterviewSessionMemory> findBySessionIdAndUserId(Long sessionId, Long userId);
    List<InterviewSessionMemory> findTop3ByUserIdAndResumeIdAndJobIdOrderByUpdatedAtDescIdDesc(
            Long userId, Long resumeId, Long jobId
    );
    long deleteByUserIdAndResumeIdAndJobId(Long userId, Long resumeId, Long jobId);
}
