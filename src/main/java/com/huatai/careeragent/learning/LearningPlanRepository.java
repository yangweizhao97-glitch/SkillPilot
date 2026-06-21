package com.huatai.careeragent.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface LearningPlanRepository extends JpaRepository<LearningPlan, Long> {
    Optional<LearningPlan> findByUserIdAndTaskId(Long userId, Long taskId);
    Optional<LearningPlan> findByUserIdAndTaskIdAndGenerationStatus(Long userId, Long taskId,
                                                                    String generationStatus);
    Optional<LearningPlan> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LearningPlan p where p.userId = :userId and p.taskId = :taskId")
    Optional<LearningPlan> findLockedByUserIdAndTaskId(@Param("userId") Long userId,
                                                       @Param("taskId") Long taskId);
}
