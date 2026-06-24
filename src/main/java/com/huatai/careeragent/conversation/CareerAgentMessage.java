package com.huatai.careeragent.conversation;

import com.huatai.careeragent.conversation.CareerAgentDtos.AgentMessageRole;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentMessageType;
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
import java.util.Map;

@Entity
@Table(name = "career_agent_messages")
public class CareerAgentMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentMessageRole role;
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private AgentMessageType messageType;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "task_id")
    private Long taskId;
    @Column(name = "report_id")
    private Long reportId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = Map.of();
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CareerAgentMessage() {
    }

    public CareerAgentMessage(Long userId, Long conversationId, AgentMessageRole role, AgentMessageType messageType,
                              String content, Long taskId, Long reportId, Map<String, Object> metadata) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.role = role;
        this.messageType = messageType;
        this.content = content;
        this.taskId = taskId;
        this.reportId = reportId;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public AgentMessageRole getRole() {
        return role;
    }

    public AgentMessageType getMessageType() {
        return messageType;
    }

    public String getContent() {
        return content;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getReportId() {
        return reportId;
    }

    public Map<String, Object> getMetadata() {
        return metadata == null ? Map.of() : metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
