package com.huatai.careeragent.task.log;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentExecutionLogRepository extends JpaRepository<AgentExecutionLog, Long> {
    List<AgentExecutionLog> findByTaskIdAndUserIdOrderByCreatedAtAscIdAsc(Long taskId, Long userId);
}
