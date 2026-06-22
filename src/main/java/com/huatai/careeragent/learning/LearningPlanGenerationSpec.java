package com.huatai.careeragent.learning;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record LearningPlanGenerationSpec(
        LearningPlanMode requestedMode,
        LearningPlanMode resolvedMode,
        LocalDate interviewDate,
        Integer daysRemaining,
        int availableHoursPerDay,
        int durationWeeks,
        String targetIndustry,
        String targetCompany,
        String targetPosition,
        String experienceLevel,
        List<String> focusAreas
) {
    public Map<String, Object> requestJson() {
        java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("requestedMode", requestedMode.name());
        value.put("resolvedMode", resolvedMode.name());
        value.put("availableHoursPerDay", availableHoursPerDay);
        value.put("durationWeeks", durationWeeks);
        if (interviewDate != null) value.put("interviewDate", interviewDate.toString());
        if (daysRemaining != null) value.put("daysRemaining", daysRemaining);
        if (targetIndustry != null) value.put("targetIndustry", targetIndustry);
        if (targetCompany != null) value.put("targetCompany", targetCompany);
        if (targetPosition != null) value.put("targetPosition", targetPosition);
        if (experienceLevel != null) value.put("experienceLevel", experienceLevel);
        value.put("focusAreas", focusAreas);
        return Map.copyOf(value);
    }

    public static LearningPlanGenerationSpec legacy() {
        return new LearningPlanGenerationSpec(LearningPlanMode.LONG_TERM, LearningPlanMode.LONG_TERM,
                null, null, 1, 8, null, null, null, null, List.of());
    }
}
