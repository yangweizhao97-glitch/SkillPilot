package com.huatai.careeragent.learning;

import java.time.Instant;
import java.util.Map;

public record LearningPlanResponse(Long planId, Long taskId, Long reportId, Map<String, Object> plan,
                                   String schemaVersion, Instant createdAt, Instant updatedAt) {
    public static LearningPlanResponse from(LearningPlan value) {
        if (!"READY".equals(value.getGenerationStatus())) {
            throw new IllegalStateException("Learning plan is not ready");
        }
        return new LearningPlanResponse(value.getId(), value.getTaskId(), value.getReportId(), value.getResultJson(),
                value.getSchemaVersion(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
