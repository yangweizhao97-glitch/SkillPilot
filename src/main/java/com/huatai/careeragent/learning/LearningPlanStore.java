package com.huatai.careeragent.learning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.UUID;

@Service
public class LearningPlanStore {
    private final LearningPlanRepository repository;
    private final ObjectMapper objectMapper;

    public LearningPlanStore(LearningPlanRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String claim(Long userId, Long taskId, Long reportId) {
        String generationId = UUID.randomUUID().toString();
        LearningPlan plan = repository.findByUserIdAndTaskId(userId, taskId).orElse(null);
        if (plan == null) {
            repository.saveAndFlush(LearningPlan.generating(userId, taskId, reportId, generationId));
            return generationId;
        }
        if ("GENERATING".equals(plan.getGenerationStatus())) {
            throw new IllegalStateException("Learning plan generation is already running");
        }
        plan.restart(reportId, generationId);
        return generationId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LearningPlanResponse complete(Long userId, Long taskId, Long reportId, String generationId, JsonNode result) {
        LearningPlan plan = repository.findByUserIdAndTaskId(userId, taskId)
                .orElseThrow(() -> new IllegalStateException("Learning plan claim not found"));
        if (!plan.getReportId().equals(reportId) || !generationId.equals(plan.getGenerationId())) {
            throw new IllegalStateException("Learning plan generation claim changed");
        }
        plan.complete(objectMapper.convertValue(result, new TypeReference<>() { }));
        return LearningPlanResponse.from(plan);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long userId, Long taskId, String generationId) {
        repository.findByUserIdAndTaskId(userId, taskId)
                .filter(plan -> generationId.equals(plan.getGenerationId()))
                .ifPresent(LearningPlan::fail);
    }
}
