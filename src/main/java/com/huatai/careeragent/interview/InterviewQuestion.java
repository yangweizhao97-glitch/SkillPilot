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
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "interview_questions")
public class InterviewQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "resume_id", nullable = false) private Long resumeId;
    @Column(name = "job_id", nullable = false) private Long jobId;
    @Column(name = "question_text", nullable = false) private String questionText;
    @Enumerated(EnumType.STRING) @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private QuestionDifficulty difficulty;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "expected_points", nullable = false, columnDefinition = "jsonb")
    private List<String> expectedPoints;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> citations;
    @Column(name = "no_citation_reason") private String noCitationReason;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected InterviewQuestion() { }

    public InterviewQuestion(Long userId, Long resumeId, Long jobId, String questionText, QuestionType questionType,
                             QuestionDifficulty difficulty, List<String> expectedPoints, List<String> citations,
                             String noCitationReason) {
        this.userId = userId;
        this.resumeId = resumeId;
        this.jobId = jobId;
        this.questionText = questionText;
        this.questionType = questionType;
        this.difficulty = difficulty;
        this.expectedPoints = List.copyOf(expectedPoints);
        this.citations = List.copyOf(citations);
        this.noCitationReason = noCitationReason;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getResumeId() { return resumeId; }
    public Long getJobId() { return jobId; }
    public String getQuestionText() { return questionText; }
    public QuestionType getQuestionType() { return questionType; }
    public QuestionDifficulty getDifficulty() { return difficulty; }
    public List<String> getExpectedPoints() { return expectedPoints; }
    public List<String> getCitations() { return citations; }
    public String getNoCitationReason() { return noCitationReason; }
    public Instant getCreatedAt() { return createdAt; }
}
