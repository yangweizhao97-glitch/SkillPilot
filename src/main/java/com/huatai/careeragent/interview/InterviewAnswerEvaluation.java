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
@Table(name = "interview_answer_evaluations")
public class InterviewAnswerEvaluation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "session_id", nullable = false) private Long sessionId;
    @Column(name = "question_id", nullable = false) private Long questionId;
    @Column(name = "answer_message_id", nullable = false, unique = true) private Long answerMessageId;
    @Column(name = "overall_score", nullable = false) private int overallScore;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultJson;
    @Column(name = "schema_version", nullable = false, length = 32) private String schemaVersion;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected InterviewAnswerEvaluation() { }

    public InterviewAnswerEvaluation(Long userId, Long sessionId, Long questionId, Long answerMessageId,
                                     int overallScore, Map<String, Object> resultJson) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.answerMessageId = answerMessageId;
        this.overallScore = overallScore;
        this.resultJson = Map.copyOf(resultJson);
        this.schemaVersion = "1.0";
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSessionId() { return sessionId; }
    public Long getQuestionId() { return questionId; }
    public Long getAnswerMessageId() { return answerMessageId; }
    public int getOverallScore() { return overallScore; }
    public Map<String, Object> getResultJson() { return resultJson; }
    public String getSchemaVersion() { return schemaVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
