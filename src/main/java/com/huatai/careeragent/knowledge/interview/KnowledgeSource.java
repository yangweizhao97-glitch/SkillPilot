package com.huatai.careeragent.knowledge.interview;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "knowledge_sources")
public class KnowledgeSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;
    @Column(length = 64) private String platform;
    @Column(name = "source_url", length = 1000) private String sourceUrl;
    @Column(nullable = false) private String title;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "collected_at", nullable = false) private Instant collectedAt;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
    @Enumerated(EnumType.STRING) @Column(name = "copyright_status", nullable = false, length = 32)
    private CopyrightStatus copyrightStatus;
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false, length = 32)
    private ReviewStatus reviewStatus;
    @Column(name = "quality_score", nullable = false, precision = 5, scale = 4) private BigDecimal qualityScore;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "reviewed_by") private Long reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected KnowledgeSource() { }

    public KnowledgeSource(SourceType sourceType, String platform, String sourceUrl, String title,
                           Instant publishedAt, String contentHash, CopyrightStatus copyrightStatus,
                           BigDecimal qualityScore, Long createdBy) {
        this.sourceType = sourceType;
        this.platform = platform;
        this.sourceUrl = sourceUrl;
        this.title = title;
        this.publishedAt = publishedAt;
        this.collectedAt = Instant.now();
        this.contentHash = contentHash;
        this.copyrightStatus = copyrightStatus;
        this.reviewStatus = ReviewStatus.PENDING;
        this.qualityScore = qualityScore;
        this.createdBy = createdBy;
    }

    public void approve(Long reviewerId) {
        reviewStatus = ReviewStatus.APPROVED;
        reviewedBy = reviewerId;
        reviewedAt = Instant.now();
        rejectionReason = null;
    }

    public void reject(Long reviewerId, String reason) {
        reviewStatus = ReviewStatus.REJECTED;
        reviewedBy = reviewerId;
        reviewedAt = Instant.now();
        rejectionReason = reason;
    }

    public Long getId() { return id; }
    public SourceType getSourceType() { return sourceType; }
    public String getPlatform() { return platform; }
    public String getSourceUrl() { return sourceUrl; }
    public String getTitle() { return title; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCollectedAt() { return collectedAt; }
    public String getContentHash() { return contentHash; }
    public CopyrightStatus getCopyrightStatus() { return copyrightStatus; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public BigDecimal getQualityScore() { return qualityScore; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }

    public enum SourceType { MANUAL, USER_SHARED, WEB, IMPORT }
    public enum CopyrightStatus { AUTHORIZED, USER_CONSENT, PUBLIC_SUMMARY }
    public enum ReviewStatus { PENDING, APPROVED, REJECTED }
}
