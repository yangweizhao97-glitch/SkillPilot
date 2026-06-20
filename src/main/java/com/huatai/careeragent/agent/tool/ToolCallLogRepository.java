package com.huatai.careeragent.agent.tool;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolCallLogRepository extends JpaRepository<ToolCallLog, Long> {
    List<ToolCallLog> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);
}
