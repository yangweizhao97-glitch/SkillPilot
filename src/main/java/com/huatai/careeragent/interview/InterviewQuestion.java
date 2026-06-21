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
import java.util.Map;

@Entity
@Table(name = "interview_questions")
public class InterviewQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "resume_id", nullable = false) private Long resumeId;
    @Column(name = "job_id", nullable = false) private Long jobId;
    @Column(name = "task_id") private Long taskId;
    @Column(name = "question_text", nullable = false) private String questionText;
    @Enumerated(EnumType.STRING) @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private QuestionDifficulty difficulty;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "expected_points", nullable = false, columnDefinition = "jsonb")
    private List<String> expectedPoints;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> citations;
    @Column(name = "no_citation_reason") private String noCitationReason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "answer_outline", nullable = false, columnDefinition = "jsonb")
    private List<String> answerOutline = List.of();
    @Column(name = "reference_answer", columnDefinition = "text") private String referenceAnswer;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "scoring_rubric", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> scoringRubric = List.of();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "common_mistakes", nullable = false, columnDefinition = "jsonb")
    private List<String> commonMistakes = List.of();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "follow_up_candidates", nullable = false, columnDefinition = "jsonb")
    private List<String> followUpCandidates = List.of();
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected InterviewQuestion() { }

    public InterviewQuestion(Long userId, Long resumeId, Long jobId, Long taskId, String questionText,
                             QuestionType questionType,
                             QuestionDifficulty difficulty, List<String> expectedPoints, List<String> citations,
                             String noCitationReason) {
        this(userId, resumeId, jobId, taskId, questionText, questionType, difficulty, expectedPoints,
                citations, noCitationReason, List.of(), null, List.of(), List.of(), List.of());
    }

    public InterviewQuestion(Long userId, Long resumeId, Long jobId, Long taskId, String questionText,
                             QuestionType questionType, QuestionDifficulty difficulty,
                             List<String> expectedPoints, List<String> citations, String noCitationReason,
                             List<String> answerOutline, String referenceAnswer,
                             List<Map<String, Object>> scoringRubric, List<String> commonMistakes,
                             List<String> followUpCandidates) {
        this.userId = userId;
        this.resumeId = resumeId;
        this.jobId = jobId;
        this.taskId = taskId;
        this.questionText = questionText;
        this.questionType = questionType;
        this.difficulty = difficulty;
        this.expectedPoints = List.copyOf(expectedPoints);
        this.citations = List.copyOf(citations);
        this.noCitationReason = noCitationReason;
        this.answerOutline = List.copyOf(answerOutline);
        this.referenceAnswer = referenceAnswer;
        this.scoringRubric = scoringRubric.stream().map(Map::copyOf).toList();
        this.commonMistakes = List.copyOf(commonMistakes);
        this.followUpCandidates = List.copyOf(followUpCandidates);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getResumeId() { return resumeId; }
    public Long getJobId() { return jobId; }
    public Long getTaskId() { return taskId; }
    public String getQuestionText() { return questionText; }
    public QuestionType getQuestionType() { return questionType; }
    public QuestionDifficulty getDifficulty() { return difficulty; }
    public List<String> getExpectedPoints() { return expectedPoints; }
    public List<String> getCitations() { return citations; }
    public String getNoCitationReason() { return noCitationReason; }
    public List<String> getAnswerOutline() { return answerOutline; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public List<Map<String, Object>> getScoringRubric() { return scoringRubric; }
    public List<String> getCommonMistakes() { return commonMistakes; }
    public List<String> getFollowUpCandidates() { return followUpCandidates; }
    public Instant getCreatedAt() { return createdAt; }
}
