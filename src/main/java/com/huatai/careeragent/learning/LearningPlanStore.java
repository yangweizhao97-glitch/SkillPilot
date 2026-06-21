package com.huatai.careeragent.learning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningPlanStore {
    private final LearningPlanRepository repository;
    private final ObjectMapper objectMapper;

    public LearningPlanStore(LearningPlanRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LearningPlanResponse save(Long userId, Long taskId, Long reportId, JsonNode result) {
        return repository.findByUserIdAndTaskId(userId, taskId)
                .map(LearningPlanResponse::from)
                .orElseGet(() -> LearningPlanResponse.from(repository.save(new LearningPlan(
                        userId, taskId, reportId, objectMapper.convertValue(result, new TypeReference<>() { })
                ))));
    }
}
