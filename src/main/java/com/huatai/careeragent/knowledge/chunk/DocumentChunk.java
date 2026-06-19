package com.huatai.careeragent.knowledge.chunk;

import com.huatai.careeragent.file.FileType;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private FileType sourceType;

    @Column(name = "source_title", nullable = false)
    private String sourceTitle;

    @Column(name = "source_locator")
    private String sourceLocator;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentChunk() {
    }

    public DocumentChunk(Long userId, Long documentId, TextChunk chunk) {
        this.userId = userId;
        this.documentId = documentId;
        this.chunkIndex = chunk.chunkIndex();
        this.content = chunk.content();
        this.tokenCount = chunk.tokenCount();
        this.sourceType = chunk.sourceType();
        this.sourceTitle = chunk.sourceTitle();
        this.sourceLocator = chunk.sourceLocator();
        this.metadata = Map.of("sourceLocator", chunk.sourceLocator());
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public FileType getSourceType() {
        return sourceType;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public String getSourceLocator() {
        return sourceLocator;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
