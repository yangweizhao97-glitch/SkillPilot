package com.huatai.careeragent.task.log;

import com.huatai.careeragent.task.WorkflowStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.huatai.careeragent.agent.tool.ToolCallLog;
import com.huatai.careeragent.agent.tool.ToolCallStatus;

public final class TaskLogDtos {
    private TaskLogDtos() {
    }

    public record TaskLogResponse(Long taskId, String traceId, List<TaskLogItem> items,
                                  List<ToolCallItem> toolCalls) {
    }

    public record ToolCallItem(String toolCallId, String agentName, String toolName,
                               Map<String, Object> inputSummary, Map<String, Object> resultSummary,
                               ToolCallStatus status, Long durationMs, String errorMessage, Instant updatedAt) {
        public static ToolCallItem from(ToolCallLog log) {
            return new ToolCallItem(log.getToolCallId(), log.getAgentName(), log.getToolName(), log.getInput(),
                    log.getOutput(), log.getStatus(), log.getDurationMs(), log.getErrorMessage(), log.getCreatedAt());
        }
    }

    public record TaskLogItem(
            Long logId,
            String traceId,
            String agentName,
            String stepName,
            WorkflowStatus workflowStatus,
            int progress,
            ExecutionLogStatus status,
            String outputSummary,
            String errorMessage,
            Long durationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Instant updatedAt
    ) {
        public static TaskLogItem from(AgentExecutionLog log) {
            WorkflowStatus workflowStatus = WorkflowStatus.valueOf(log.getStepName());
            return new TaskLogItem(
                    log.getId(),
                    log.getTraceId(),
                    log.getAgentName(),
                    log.getStepName(),
                    workflowStatus,
                    workflowStatus.progress(),
                    log.getStatus(),
                    log.getOutputSummary(),
                    log.getErrorMessage(),
                    log.getDurationMs(),
                    log.getPromptTokens(),
                    log.getCompletionTokens(),
                    log.getTotalTokens(),
                    log.getCreatedAt()
            );
        }
    }
}
