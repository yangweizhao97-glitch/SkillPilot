package com.huatai.careeragent.agent.tool;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ToolCallLogRepository extends JpaRepository<ToolCallLog, Long> {
    List<ToolCallLog> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);
    List<ToolCallLog> findByTaskIdAndUserIdOrderByCreatedAtAscIdAsc(Long taskId, Long userId);
    List<ToolCallLog> findByScopeTypeAndScopeIdAndUserIdOrderByCreatedAtAscIdAsc(
            ToolScopeType scopeType, Long scopeId, Long userId
    );
    Optional<ToolCallLog> findByToolCallId(String toolCallId);
}
