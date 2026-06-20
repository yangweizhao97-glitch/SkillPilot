package com.huatai.careeragent.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {
    List<InterviewMessage> findByUserIdAndSessionIdOrderBySequenceNoAsc(Long userId, Long sessionId);

    @Query("select coalesce(max(m.sequenceNo), 0) from InterviewMessage m where m.sessionId = :sessionId")
    int maxSequence(@Param("sessionId") Long sessionId);
}
