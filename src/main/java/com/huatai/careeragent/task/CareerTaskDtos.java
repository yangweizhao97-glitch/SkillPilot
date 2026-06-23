package com.huatai.careeragent.task;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class CareerTaskDtos {
    private CareerTaskDtos() {
    }

    public record CreateCareerTaskRequest(
            @NotNull Long resumeId,
            @NotNull Long jobId,
            List<WorkflowStatus> enabledSteps,
            List<WorkflowStatus> optionalSteps
    ) {
    }

    public record CareerTaskResponse(
            Long taskId,
            String traceId,
            CareerTaskType taskType,
            WorkflowStatus status,
            int progress,
            Long resumeId,
            Long jobId,
            List<WorkflowStatus> enabledSteps,
            List<WorkflowStatus> optionalSteps,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant finishedAt
    ) {
        public static CareerTaskResponse from(AgentTask task) {
            return new CareerTaskResponse(
                    task.getId(), task.getTraceId(), task.getTaskType(), task.getStatus(), task.getProgress(),
                    task.getResumeId(), task.getJobId(), task.getEnabledSteps(), task.getOptionalSteps(),
                    task.getErrorMessage(), task.getCreatedAt(), task.getUpdatedAt(), task.getStartedAt(),
                    task.getFinishedAt()
            );
        }
    }
}
