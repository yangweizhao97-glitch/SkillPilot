package com.huatai.careeragent.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "company")
    private String company;

    @Column(name = "position", nullable = false)
    private String position;

    @Column(name = "jd_text", nullable = false)
    private String jdText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Job() {
    }

    public Job(Long userId, Long documentId, String company, String position, String jdText) {
        this.userId = userId;
        this.documentId = documentId;
        this.company = company;
        this.position = position;
        this.jdText = jdText;
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

    public String getCompany() {
        return company;
    }

    public String getPosition() {
        return position;
    }

    public String getJdText() {
        return jdText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
