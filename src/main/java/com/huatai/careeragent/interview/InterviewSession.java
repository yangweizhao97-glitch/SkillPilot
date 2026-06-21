package com.huatai.careeragent.interview;

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
import java.util.List;

@Entity
@Table(name = "interview_sessions")
public class InterviewSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "resume_id", nullable = false) private Long resumeId;
    @Column(name = "job_id", nullable = false) private Long jobId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private InterviewSessionStatus status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "question_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> questionIds;
    @Column(name = "current_question_index", nullable = false) private int currentQuestionIndex;
    @Column(name = "follow_up_count", nullable = false) private int followUpCount;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "processing_answer", nullable = false) private boolean processingAnswer;
    @Column(name = "processing_started_at") private Instant processingStartedAt;
    @Column(name = "processing_message_id") private Long processingMessageId;

    protected InterviewSession() { }

    public InterviewSession(Long userId, Long resumeId, Long jobId, List<Long> questionIds) {
        this.userId = userId;
        this.resumeId = resumeId;
        this.jobId = jobId;
        this.questionIds = List.copyOf(questionIds);
        this.status = InterviewSessionStatus.IN_PROGRESS;
    }

    public Long currentQuestionId() { return questionIds.get(currentQuestionIndex); }
    public boolean hasNextQuestion() { return currentQuestionIndex + 1 < questionIds.size(); }
    public void advance() { currentQuestionIndex++; followUpCount = 0; }
    public void recordFollowUp() { followUpCount++; }
    public void finish() { status = InterviewSessionStatus.FINISHED; finishedAt = Instant.now(); }
    public boolean isProcessingAnswer() { return processingAnswer; }
    public boolean processingIsStale(Instant threshold) {
        return processingAnswer && (processingStartedAt == null || processingStartedAt.isBefore(threshold));
    }
    public void beginAnswerProcessing() { processingAnswer = true; processingStartedAt = Instant.now(); processingMessageId = null; }
    public void attachProcessingMessage(Long messageId) { processingMessageId = messageId; }
    public void endAnswerProcessing() { processingAnswer = false; processingStartedAt = null; processingMessageId = null; }
    public Long getProcessingMessageId() { return processingMessageId; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getResumeId() { return resumeId; }
    public Long getJobId() { return jobId; }
    public InterviewSessionStatus getStatus() { return status; }
    public List<Long> getQuestionIds() { return List.copyOf(questionIds); }
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public int getFollowUpCount() { return followUpCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
