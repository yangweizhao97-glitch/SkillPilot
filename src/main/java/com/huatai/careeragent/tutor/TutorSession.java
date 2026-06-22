package com.huatai.careeragent.tutor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tutor_sessions")
public class TutorSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "resume_id") private Long resumeId;
    @Column(name = "job_id") private Long jobId;
    @Column(name = "question_id") private Long questionId;
    @Column(name = "evaluation_id") private Long evaluationId;
    @Column(name = "learning_plan_id") private Long learningPlanId;
    @Column(nullable = false) private boolean processing;
    @Column(name = "processing_started_at") private Instant processingStartedAt;
    @Column(name = "memory_summary", nullable = false, columnDefinition = "text") private String memorySummary = "";
    @Column(name = "memory_through_sequence", nullable = false) private int memoryThroughSequence;
    @Column(name = "memory_revision", nullable = false) private int memoryRevision;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected TutorSession() { }

    public TutorSession(Long userId, String title, Long resumeId, Long jobId, Long questionId,
                        Long evaluationId, Long learningPlanId) {
        this.userId = userId;
        this.title = title;
        this.resumeId = resumeId;
        this.jobId = jobId;
        this.questionId = questionId;
        this.evaluationId = evaluationId;
        this.learningPlanId = learningPlanId;
    }

    public void beginProcessing() { processing = true; processingStartedAt = Instant.now(); }
    public void endProcessing() { processing = false; processingStartedAt = null; }
    public boolean processingIsStale(Instant threshold) {
        return processing && (processingStartedAt == null || processingStartedAt.isBefore(threshold));
    }
    public void updateMemory(String summary, int throughSequence) {
        memorySummary = summary == null ? "" : summary;
        memoryThroughSequence = Math.max(memoryThroughSequence, throughSequence);
        memoryRevision++;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public Long getResumeId() { return resumeId; }
    public Long getJobId() { return jobId; }
    public Long getQuestionId() { return questionId; }
    public Long getEvaluationId() { return evaluationId; }
    public Long getLearningPlanId() { return learningPlanId; }
    public boolean isProcessing() { return processing; }
    public String getMemorySummary() { return memorySummary == null ? "" : memorySummary; }
    public int getMemoryThroughSequence() { return memoryThroughSequence; }
    public int getMemoryRevision() { return memoryRevision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
