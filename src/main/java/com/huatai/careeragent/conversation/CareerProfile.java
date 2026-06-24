package com.huatai.careeragent.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "career_profiles")
public class CareerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_roles", nullable = false, columnDefinition = "jsonb")
    private List<String> targetRoles = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "career_stages", nullable = false, columnDefinition = "jsonb")
    private List<String> careerStages = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weakness_tags", nullable = false, columnDefinition = "jsonb")
    private List<String> weaknessTags = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preference_tags", nullable = false, columnDefinition = "jsonb")
    private List<String> preferenceTags = new ArrayList<>();
    @Column(nullable = false, columnDefinition = "text")
    private String summary = "";
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareerProfile() {
    }

    public CareerProfile(Long userId) {
        this.userId = userId;
    }

    public void merge(List<String> roles, List<String> stages, List<String> weaknesses, List<String> preferences) {
        targetRoles = mergeTags(targetRoles, roles);
        careerStages = mergeTags(careerStages, stages);
        weaknessTags = mergeTags(weaknessTags, weaknesses);
        preferenceTags = mergeTags(preferenceTags, preferences);
        summary = buildSummary();
    }

    private static List<String> mergeTags(List<String> current, List<String> incoming) {
        Set<String> merged = new LinkedHashSet<>();
        if (current != null) merged.addAll(current);
        if (incoming != null) merged.addAll(incoming);
        return merged.stream().filter(item -> item != null && !item.isBlank()).limit(12).toList();
    }

    private String buildSummary() {
        List<String> parts = new ArrayList<>();
        if (!targetRoles.isEmpty()) parts.add("目标：" + String.join("、", targetRoles));
        if (!careerStages.isEmpty()) parts.add("阶段：" + String.join("、", careerStages));
        if (!weaknessTags.isEmpty()) parts.add("关注薄弱点：" + String.join("、", weaknessTags));
        return String.join("；", parts);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public List<String> getTargetRoles() {
        return targetRoles == null ? List.of() : targetRoles;
    }

    public List<String> getCareerStages() {
        return careerStages == null ? List.of() : careerStages;
    }

    public List<String> getWeaknessTags() {
        return weaknessTags == null ? List.of() : weaknessTags;
    }

    public List<String> getPreferenceTags() {
        return preferenceTags == null ? List.of() : preferenceTags;
    }

    public String getSummary() {
        return summary == null ? "" : summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
