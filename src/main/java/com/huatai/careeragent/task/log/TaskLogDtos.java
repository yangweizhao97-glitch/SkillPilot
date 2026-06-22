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
                                  List<ToolCallItem> toolCalls, List<UserVisibleStep> steps,
                                  List<TechnicalDetail> technicalDetails) {
    }

    public record UserTaskProgressResponse(Long taskId, List<UserVisibleStep> steps,
                                           List<TechnicalDetail> technicalDetails) {
    }

    public enum UserEventType {
        STEP_STARTED, STEP_PROGRESS, STEP_COMPLETED, TASK_COMPLETED, TASK_FAILED
    }

    public enum UserStep {
        READING_INPUTS,
        ANALYZING_REQUIREMENTS,
        RETRIEVING_CONTEXT,
        JOB_MATCHING,
        RESUME_OPTIMIZATION,
        INTERVIEW_QUESTIONS,
        FINAL_REPORT
    }

    public enum UserStepStatus {
        RUNNING, SUCCESS, FAILED
    }

    public record UserVisibleStep(
            UserEventType type,
            UserStep step,
            String title,
            String summary,
            UserStepStatus status,
            Instant startedAt,
            Instant completedAt,
            Long durationMs,
            List<String> progress,
            List<StepDetail> details
    ) {
    }

    public record StepDetail(String label, String status, Long durationMs, Instant occurredAt,
                             String source) {
    }

    /**
     * Deliberately excludes trace ids, prompts, token counts and tool payloads. The full records remain in the
     * audit tables; this projection is the only debugging data intended for the end-user screen.
     */
    public record TechnicalDetail(String category, String label, String status, Long durationMs,
                                  Instant occurredAt, String safeSummary) {
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
