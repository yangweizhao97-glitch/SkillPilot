package com.huatai.careeragent.interview;

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
@Table(name = "interview_session_reviews")
public class InterviewSessionReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "session_id", nullable = false, unique = true) private Long sessionId;
    @Column(name = "overall_score", nullable = false) private int overallScore;
    @Column(name = "evaluated_answers", nullable = false) private int evaluatedAnswers;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultJson;
    @Column(name = "schema_version", nullable = false, length = 32) private String schemaVersion;
    @Column(name = "generation_source", nullable = false, length = 32) private String generationSource;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected InterviewSessionReview() { }

    public InterviewSessionReview(Long userId, Long sessionId, int overallScore, int evaluatedAnswers,
                                  Map<String, Object> resultJson, String generationSource) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.overallScore = overallScore;
        this.evaluatedAnswers = evaluatedAnswers;
        this.resultJson = Map.copyOf(resultJson);
        this.schemaVersion = "1.0";
        this.generationSource = generationSource;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSessionId() { return sessionId; }
    public int getOverallScore() { return overallScore; }
    public int getEvaluatedAnswers() { return evaluatedAnswers; }
    public Map<String, Object> getResultJson() { return resultJson; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getGenerationSource() { return generationSource; }
    public Instant getCreatedAt() { return createdAt; }
}
