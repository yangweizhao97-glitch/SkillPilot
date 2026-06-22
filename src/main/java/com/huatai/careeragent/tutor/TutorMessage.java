package com.huatai.careeragent.tutor;

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
@Table(name = "tutor_messages")
public class TutorMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "session_id", nullable = false) private Long sessionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TutorMessageRole role;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> citations;
    @Column(name = "sequence_no", nullable = false) private int sequenceNo;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected TutorMessage() { }

    public TutorMessage(Long userId, Long sessionId, TutorMessageRole role, String content,
                        List<Map<String, Object>> citations, int sequenceNo) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.citations = citations.stream().map(Map::copyOf).toList();
        this.sequenceNo = sequenceNo;
    }

    public Long getId() { return id; }
    public TutorMessageRole getRole() { return role; }
    public String getContent() { return content; }
    public List<Map<String, Object>> getCitations() { return citations; }
    public int getSequenceNo() { return sequenceNo; }
    public Instant getCreatedAt() { return createdAt; }
}
