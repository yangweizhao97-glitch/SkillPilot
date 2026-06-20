package com.huatai.careeragent.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    Page<Job> findByUserId(Long userId, Pageable pageable);

    Optional<Job> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id and j.userId = :userId")
    Optional<Job> findLockedByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
