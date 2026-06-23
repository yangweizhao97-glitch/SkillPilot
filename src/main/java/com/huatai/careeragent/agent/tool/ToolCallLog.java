package com.huatai.careeragent.agent.tool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tool_call_logs")
public class ToolCallLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_call_id", nullable = false, unique = true, length = 36)
    private String toolCallId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id")
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 32)
    private ToolScopeType scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "trace_id", nullable = false, length = 80)
    private String traceId;

    @Column(name = "agent_name", nullable = false, length = 120)
    private String agentName;

    @Column(name = "tool_name", nullable = false, length = 120)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input", columnDefinition = "jsonb")
    private Map<String, Object> input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", columnDefinition = "jsonb")
    private Map<String, Object> output;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ToolCallStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ToolCallLog() {
    }

    public ToolCallLog(
            ToolExecutionContext context,
            String toolName,
            Map<String, Object> input,
            Map<String, Object> output,
            ToolCallStatus status,
            long durationMs,
            String errorMessage,
            int retryCount
    ) {
        this.userId = context.userId();
        this.toolCallId = UUID.randomUUID().toString();
        this.taskId = context.taskId();
        this.scopeType = context.scopeType();
        this.scopeId = context.scopeId();
        this.traceId = context.traceId();
        this.agentName = context.agentName();
        this.toolName = toolName;
        this.input = input;
        this.output = output;
        this.status = status;
        this.durationMs = durationMs;
        this.errorMessage = summarize(errorMessage);
        this.retryCount = retryCount;
    }

    public void complete(Map<String, Object> output, ToolCallStatus status, long durationMs, String errorMessage) {
        this.output = output;
        this.status = status;
        this.durationMs = durationMs;
        this.errorMessage = summarize(errorMessage);
    }

    private String summarize(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public Long getId() { return id; }
    public String getToolCallId() { return toolCallId; }
    public Long getUserId() { return userId; }
    public Long getTaskId() { return taskId; }
    public ToolScopeType getScopeType() { return scopeType; }
    public Long getScopeId() { return scopeId; }
    public String getTraceId() { return traceId; }
    public String getAgentName() { return agentName; }
    public String getToolName() { return toolName; }
    public Map<String, Object> getInput() { return input; }
    public Map<String, Object> getOutput() { return output; }
    public ToolCallStatus getStatus() { return status; }
    public Long getDurationMs() { return durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
}
