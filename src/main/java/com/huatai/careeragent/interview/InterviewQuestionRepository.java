package com.huatai.careeragent.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long>,
        JpaSpecificationExecutor<InterviewQuestion> {
    List<InterviewQuestion> findByUserIdAndTaskIdOrderByCreatedAtAscIdAsc(Long userId, Long taskId);
    List<InterviewQuestion> findByUserIdAndResumeIdAndJobIdOrderByCreatedAtDescIdDesc(
            Long userId, Long resumeId, Long jobId
    );
    Optional<InterviewQuestion> findByIdAndUserId(Long id, Long userId);
}
