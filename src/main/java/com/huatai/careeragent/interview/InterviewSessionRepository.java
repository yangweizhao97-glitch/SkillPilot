package com.huatai.careeragent.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSession s where s.id = :id and s.userId = :userId")
    Optional<InterviewSession> findLockedByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    List<InterviewSession> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
