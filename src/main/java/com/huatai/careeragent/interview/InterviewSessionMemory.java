package com.huatai.careeragent.interview;

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
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "interview_session_memories")
public class InterviewSessionMemory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "session_id", nullable = false, unique = true) private Long sessionId;
    @Column(name = "resume_id", nullable = false) private Long resumeId;
    @Column(name = "job_id", nullable = false) private Long jobId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "memory_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> memoryJson;
    @Column(nullable = false) private int revision;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected InterviewSessionMemory() { }

    public InterviewSessionMemory(Long userId, Long sessionId, Long resumeId, Long jobId,
                                  Map<String, Object> memoryJson) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.resumeId = resumeId;
        this.jobId = jobId;
        this.memoryJson = new LinkedHashMap<>(memoryJson);
        this.revision = 1;
    }

    public void update(Map<String, Object> memoryJson) {
        this.memoryJson = new LinkedHashMap<>(memoryJson);
        this.revision++;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSessionId() { return sessionId; }
    public Long getResumeId() { return resumeId; }
    public Long getJobId() { return jobId; }
    public Map<String, Object> getMemoryJson() { return Map.copyOf(memoryJson); }
    public int getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
