package com.huatai.careeragent.task.log;

import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.WorkflowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "agent_execution_logs")
public class AgentExecutionLog {
    public static final String WORKFLOW_AGENT = "CAREER_WORKFLOW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "trace_id", nullable = false, length = 80)
    private String traceId;

    @Column(name = "agent_name", nullable = false, length = 120)
    private String agentName;

    @Column(name = "step_name", nullable = false, length = 120)
    private String stepName;

    @Column(name = "input_summary")
    private String inputSummary;

    @Column(name = "output_summary")
    private String outputSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExecutionLogStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentExecutionLog() {
    }

    private AgentExecutionLog(AgentTask task, WorkflowStatus workflowStatus, ExecutionLogStatus status, String errorMessage) {
        this.userId = task.getUserId();
        this.taskId = task.getId();
        this.traceId = task.getTraceId();
        this.agentName = WORKFLOW_AGENT;
        this.stepName = workflowStatus.name();
        this.outputSummary = "Workflow status changed to " + workflowStatus.name();
        this.status = status;
        this.errorMessage = summarize(errorMessage);
    }

    public static AgentExecutionLog transition(AgentTask task, WorkflowStatus workflowStatus) {
        return new AgentExecutionLog(task, workflowStatus, ExecutionLogStatus.SUCCESS, null);
    }

    public static AgentExecutionLog failure(AgentTask task, String errorMessage) {
        return new AgentExecutionLog(task, WorkflowStatus.FAILED, ExecutionLogStatus.FAILED, errorMessage);
    }

    private String summarize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getTaskId() { return taskId; }
    public String getTraceId() { return traceId; }
    public String getAgentName() { return agentName; }
    public String getStepName() { return stepName; }
    public String getInputSummary() { return inputSummary; }
    public String getOutputSummary() { return outputSummary; }
    public ExecutionLogStatus getStatus() { return status; }
    public Long getDurationMs() { return durationMs; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
