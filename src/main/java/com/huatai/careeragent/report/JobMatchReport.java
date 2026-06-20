package com.huatai.careeragent.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "job_match_reports")
public class JobMatchReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "task_id", nullable = false) private Long taskId;
    @Column(name = "resume_id", nullable = false) private Long resumeId;
    @Column(name = "job_id", nullable = false) private Long jobId;
    @Column(nullable = false) private int version;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultJson;
    @Column(name = "schema_version", nullable = false, length = 32) private String schemaVersion;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected JobMatchReport() { }

    public JobMatchReport(Long userId, Long taskId, Long resumeId, Long jobId, int version, Map<String, Object> resultJson) {
        this.userId = userId;
        this.taskId = taskId;
        this.resumeId = resumeId;
        this.jobId = jobId;
        this.version = version;
        this.resultJson = Map.copyOf(resultJson);
        this.schemaVersion = "1.0";
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getTaskId() { return taskId; }
    public Long getResumeId() { return resumeId; }
    public Long getJobId() { return jobId; }
    public int getVersion() { return version; }
    public Map<String, Object> getResultJson() { return resultJson; }
    public String getSchemaVersion() { return schemaVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
