package com.huatai.careeragent.knowledge.interview;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "interview_experiences")
public class InterviewExperience {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "source_id", nullable = false) private Long sourceId;
    private String industry;
    private String company;
    @Column(nullable = false) private String position;
    @Column(name = "experience_level") private String experienceLevel;
    @Column(name = "interview_round") private String interviewRound;
    @Column(nullable = false, columnDefinition = "text") private String summary;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private List<String> tags;
    @Column(name = "event_date") private LocalDate eventDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private PublicationStatus status;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected InterviewExperience() { }

    public InterviewExperience(Long sourceId, String industry, String company, String position,
                               String experienceLevel, String interviewRound, String summary,
                               List<String> tags, LocalDate eventDate) {
        this.sourceId = sourceId;
        this.industry = industry;
        this.company = company;
        this.position = position;
        this.experienceLevel = experienceLevel;
        this.interviewRound = interviewRound;
        this.summary = summary;
        this.tags = List.copyOf(tags);
        this.eventDate = eventDate;
        this.status = PublicationStatus.DRAFT;
    }

    public void publish() { status = PublicationStatus.PUBLISHED; }
    public void reject() { status = PublicationStatus.REJECTED; }
    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public String getIndustry() { return industry; }
    public String getCompany() { return company; }
    public String getPosition() { return position; }
    public String getExperienceLevel() { return experienceLevel; }
    public String getInterviewRound() { return interviewRound; }
    public String getSummary() { return summary; }
    public List<String> getTags() { return tags; }
    public LocalDate getEventDate() { return eventDate; }
    public PublicationStatus getStatus() { return status; }
    public enum PublicationStatus { DRAFT, PUBLISHED, REJECTED }
}
