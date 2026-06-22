package com.huatai.careeragent.tutor;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TutorSessionRepository extends JpaRepository<TutorSession, Long> {
    List<TutorSession> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId);
    Optional<TutorSession> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TutorSession s where s.id = :id and s.userId = :userId")
    Optional<TutorSession> findLockedByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
