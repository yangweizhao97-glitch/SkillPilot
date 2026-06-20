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

import java.time.Instant;

@Entity
@Table(name = "interview_messages")
public class InterviewMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "session_id", nullable = false) private Long sessionId;
    @Column(name = "question_id") private Long questionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private InterviewMessageRole role;
    @Column(nullable = false) private String content;
    @Column(name = "sequence_no", nullable = false) private int sequenceNo;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected InterviewMessage() { }

    public InterviewMessage(Long userId, Long sessionId, Long questionId, InterviewMessageRole role,
                            String content, int sequenceNo) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.role = role;
        this.content = content;
        this.sequenceNo = sequenceNo;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSessionId() { return sessionId; }
    public Long getQuestionId() { return questionId; }
    public InterviewMessageRole getRole() { return role; }
    public String getContent() { return content; }
    public int getSequenceNo() { return sequenceNo; }
    public Instant getCreatedAt() { return createdAt; }
}
