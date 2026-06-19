package com.huatai.careeragent.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    Page<Job> findByUserId(Long userId, Pageable pageable);

    Optional<Job> findByIdAndUserId(Long id, Long userId);
}
