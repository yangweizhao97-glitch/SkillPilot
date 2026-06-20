package com.huatai.careeragent.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {
    Optional<AgentTask> findByIdAndUserId(Long id, Long userId);
}
