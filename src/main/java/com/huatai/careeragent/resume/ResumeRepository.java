package com.huatai.careeragent.resume;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    Page<Resume> findByUserId(Long userId, Pageable pageable);

    Optional<Resume> findByIdAndUserId(Long id, Long userId);
}
