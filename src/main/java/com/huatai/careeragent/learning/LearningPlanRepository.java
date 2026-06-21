package com.huatai.careeragent.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningPlanRepository extends JpaRepository<LearningPlan, Long> {
    Optional<LearningPlan> findByUserIdAndTaskId(Long userId, Long taskId);
    Optional<LearningPlan> findByIdAndUserId(Long id, Long userId);
}
