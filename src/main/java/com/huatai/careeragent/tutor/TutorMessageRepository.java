package com.huatai.careeragent.tutor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TutorMessageRepository extends JpaRepository<TutorMessage, Long> {
    List<TutorMessage> findByUserIdAndSessionIdOrderBySequenceNoAsc(Long userId, Long sessionId);

    @Query("select coalesce(max(m.sequenceNo), 0) from TutorMessage m where m.sessionId = :sessionId")
    int maxSequence(@Param("sessionId") Long sessionId);
}
