package com.huatai.careeragent.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "learning_plans")
public class LearningPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "task_id", nullable = false) private Long taskId;
    @Column(name = "report_id", nullable = false) private Long reportId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultJson;
    @Column(name = "schema_version", nullable = false, length = 32) private String schemaVersion;
    @Column(name = "generation_status", nullable = false, length = 32) private String generationStatus;
    @Column(name = "generation_id", length = 64) private String generationId;
    @Column(name = "generation_started_at") private Instant generationStartedAt;
    @Column(name = "plan_mode", nullable = false, length = 24) private String planMode;
    @Column(name = "interview_date") private LocalDate interviewDate;
    @Column(name = "days_remaining") private Integer daysRemaining;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> requestJson;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected LearningPlan() { }

    public LearningPlan(Long userId, Long taskId, Long reportId, Map<String, Object> resultJson) {
        this.userId = userId;
        this.taskId = taskId;
        this.reportId = reportId;
        this.resultJson = Map.copyOf(resultJson);
        this.schemaVersion = "1.0";
        this.generationStatus = "READY";
        this.planMode = String.valueOf(resultJson.getOrDefault("planMode", "LONG_TERM"));
        this.requestJson = Map.of();
    }

    public static LearningPlan generating(Long userId, Long taskId, Long reportId, String generationId) {
        return generating(userId, taskId, reportId, generationId, LearningPlanGenerationSpec.legacy());
    }

    public static LearningPlan generating(Long userId, Long taskId, Long reportId, String generationId,
                                          LearningPlanGenerationSpec spec) {
        LearningPlan plan = new LearningPlan();
        plan.userId = userId; plan.taskId = taskId; plan.reportId = reportId;
        plan.resultJson = Map.of(); plan.schemaVersion = "1.0"; plan.generationStatus = "GENERATING";
        plan.generationId = generationId; plan.generationStartedAt = Instant.now();
        plan.applySpec(spec);
        return plan;
    }

    public void restart(Long reportId, String generationId) { restart(reportId, generationId, LearningPlanGenerationSpec.legacy()); }
    public void restart(Long reportId, String generationId, LearningPlanGenerationSpec spec) { this.reportId = reportId; this.resultJson = Map.of(); this.generationStatus = "GENERATING"; this.generationId = generationId; this.generationStartedAt = Instant.now(); applySpec(spec); }
    public boolean generationIsStale(Instant threshold) { return "GENERATING".equals(generationStatus) && (generationStartedAt == null || generationStartedAt.isBefore(threshold)); }
    public void complete(Map<String, Object> result) { this.resultJson = Map.copyOf(result); this.schemaVersion = String.valueOf(result.getOrDefault("schemaVersion", "1.0")); this.generationStatus = "READY"; clearGenerationClaim(); }
    public void fail() { this.generationStatus = "FAILED"; clearGenerationClaim(); }
    private void clearGenerationClaim() { this.generationId = null; this.generationStartedAt = null; }
    private void applySpec(LearningPlanGenerationSpec spec) { this.planMode = spec.resolvedMode().name(); this.interviewDate = spec.interviewDate(); this.daysRemaining = spec.daysRemaining(); this.requestJson = spec.requestJson(); }
    public boolean matches(LearningPlanGenerationSpec spec) { return planMode.equals(spec.resolvedMode().name()) && requestJson.equals(spec.requestJson()); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getTaskId() { return taskId; }
    public Long getReportId() { return reportId; }
    public Map<String, Object> getResultJson() { return resultJson; }
    public String getSchemaVersion() { return schemaVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getGenerationStatus() { return generationStatus; }
    public String getGenerationId() { return generationId; }
    public Instant getGenerationStartedAt() { return generationStartedAt; }
    public String getPlanMode() { return planMode; }
    public LocalDate getInterviewDate() { return interviewDate; }
    public Integer getDaysRemaining() { return daysRemaining; }
    public Map<String, Object> getRequestJson() { return requestJson; }
}
