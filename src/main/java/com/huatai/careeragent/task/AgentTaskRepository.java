package com.huatai.careeragent.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {
    Optional<AgentTask> findByIdAndUserId(Long id, Long userId);
    Page<AgentTask> findByUserId(Long userId, Pageable pageable);
}
