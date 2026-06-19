package com.huatai.careeragent.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "resumes")
public class Resume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "latest_analysis_version", nullable = false)
    private int latestAnalysisVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Resume() {
    }

    public Resume(Long userId, Long documentId, String title) {
        this.userId = userId;
        this.documentId = documentId;
        this.title = title;
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

    public String getTitle() {
        return title;
    }

    public int getLatestAnalysisVersion() {
        return latestAnalysisVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
