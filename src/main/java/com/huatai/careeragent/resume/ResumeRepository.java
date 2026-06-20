package com.huatai.careeragent.resume;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    Page<Resume> findByUserId(Long userId, Pageable pageable);

    Optional<Resume> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resume r where r.id = :id and r.userId = :userId")
    Optional<Resume> findLockedByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
