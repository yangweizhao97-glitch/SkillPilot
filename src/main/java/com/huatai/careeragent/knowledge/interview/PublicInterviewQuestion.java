package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.interview.QuestionDifficulty;
import com.huatai.careeragent.interview.QuestionType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "public_interview_questions")
public class PublicInterviewQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "experience_id", nullable = false) private Long experienceId;
    @Column(name = "normalized_question", nullable = false, columnDefinition = "text") private String normalizedQuestion;
    @Column(name = "question_hash", nullable = false, length = 64) private String questionHash;
    @Enumerated(EnumType.STRING) @Column(name = "question_type", nullable = false, length = 32) private QuestionType questionType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private QuestionDifficulty difficulty;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "knowledge_points", nullable = false, columnDefinition = "jsonb") private List<String> knowledgePoints;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "answer_outline", nullable = false, columnDefinition = "jsonb") private List<String> answerOutline;
    @Column(name = "reference_answer", columnDefinition = "text") private String referenceAnswer;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "scoring_rubric", nullable = false, columnDefinition = "jsonb") private List<Map<String, Object>> scoringRubric;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "common_mistakes", nullable = false, columnDefinition = "jsonb") private List<String> commonMistakes;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "follow_up_candidates", nullable = false, columnDefinition = "jsonb") private List<String> followUpCandidates;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private InterviewExperience.PublicationStatus status;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PublicInterviewQuestion() { }

    public PublicInterviewQuestion(Long experienceId, String normalizedQuestion, String questionHash,
                                   QuestionType questionType, QuestionDifficulty difficulty,
                                   List<String> knowledgePoints, List<String> answerOutline,
                                   String referenceAnswer, List<Map<String, Object>> scoringRubric,
                                   List<String> commonMistakes, List<String> followUpCandidates) {
        this.experienceId = experienceId;
        this.normalizedQuestion = normalizedQuestion;
        this.questionHash = questionHash;
        this.questionType = questionType;
        this.difficulty = difficulty;
        this.knowledgePoints = List.copyOf(knowledgePoints);
        this.answerOutline = List.copyOf(answerOutline);
        this.referenceAnswer = referenceAnswer;
        this.scoringRubric = scoringRubric.stream().map(Map::copyOf).toList();
        this.commonMistakes = List.copyOf(commonMistakes);
        this.followUpCandidates = List.copyOf(followUpCandidates);
        this.status = InterviewExperience.PublicationStatus.DRAFT;
    }

    public void publish() { status = InterviewExperience.PublicationStatus.PUBLISHED; }
    public void reject() { status = InterviewExperience.PublicationStatus.REJECTED; }
    public Long getId() { return id; }
    public Long getExperienceId() { return experienceId; }
    public String getNormalizedQuestion() { return normalizedQuestion; }
    public String getQuestionHash() { return questionHash; }
    public QuestionType getQuestionType() { return questionType; }
    public QuestionDifficulty getDifficulty() { return difficulty; }
    public List<String> getKnowledgePoints() { return knowledgePoints; }
    public List<String> getAnswerOutline() { return answerOutline; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public List<Map<String, Object>> getScoringRubric() { return scoringRubric; }
    public List<String> getCommonMistakes() { return commonMistakes; }
    public List<String> getFollowUpCandidates() { return followUpCandidates; }
    public InterviewExperience.PublicationStatus getStatus() { return status; }
}
