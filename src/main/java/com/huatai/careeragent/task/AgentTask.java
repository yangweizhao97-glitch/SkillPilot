package com.huatai.careeragent.task;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_tasks")
public class AgentTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trace_id", nullable = false, length = 80)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40)
    private CareerTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private WorkflowStatus status = WorkflowStatus.PENDING;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "resume_id")
    private Long resumeId;

    @Column(name = "job_id")
    private Long jobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enabled_steps", nullable = false, columnDefinition = "jsonb")
    private List<WorkflowStatus> enabledSteps = new ArrayList<>();

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentTask() {
    }

    public AgentTask(Long userId, String traceId, Long resumeId, Long jobId, List<WorkflowStatus> enabledSteps) {
        this.userId = userId;
        this.traceId = traceId;
        this.taskType = CareerTaskType.CAREER_PREPARE;
        this.resumeId = resumeId;
        this.jobId = jobId;
        this.enabledSteps = new ArrayList<>(enabledSteps);
    }

    public void transitionTo(WorkflowStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid workflow transition: " + status + " -> " + next);
        }
        Instant now = Instant.now();
        if (status == WorkflowStatus.PENDING && next == WorkflowStatus.MATCHING_JOB) {
            startedAt = now;
        }
        status = next;
        progress = next.progress();
        if (next.isTerminal()) {
            finishedAt = now;
        }
    }

    public void fail(String message) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Terminal task cannot fail again");
        }
        errorMessage = summarize(message);
        transitionTo(WorkflowStatus.FAILED);
    }

    private String summarize(String value) {
        String message = value == null || value.isBlank() ? "Workflow execution failed" : value.trim();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTraceId() { return traceId; }
    public CareerTaskType getTaskType() { return taskType; }
    public WorkflowStatus getStatus() { return status; }
    public int getProgress() { return progress; }
    public Long getResumeId() { return resumeId; }
    public Long getJobId() { return jobId; }
    public List<WorkflowStatus> getEnabledSteps() { return List.copyOf(enabledSteps); }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
