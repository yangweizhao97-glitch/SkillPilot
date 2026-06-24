package com.huatai.careeragent.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.WorkflowStatus;

import java.util.List;
import java.util.Map;
import java.time.Instant;

public final class CareerAgentDtos {
    private CareerAgentDtos() {
    }

    public enum CareerIntent {
        CAREER_ANALYSIS,
        RESUME_REVIEW,
        JOB_MATCH,
        INTERVIEW_PREP,
        MOCK_INTERVIEW,
        LEARNING_PLAN,
        REPORT_QA,
        GENERAL_CAREER_QA
    }

    public enum RequiredResource {
        RESUME,
        JOB,
        REPORT
    }

    public enum AgentNextAction {
        ASK_USER,
        START_WORKFLOW,
        GET_REPORT,
        START_MOCK_INTERVIEW,
        GENERATE_LEARNING_PLAN,
        ANSWER_DIRECTLY
    }

    public enum AgentMessageRole {
        USER,
        ASSISTANT,
        SYSTEM
    }

    public enum AgentMessageType {
        TEXT,
        PROCESS,
        TOOL_STATUS,
        RESOURCE_CARD,
        WORKFLOW_STATUS,
        REPORT_READY
    }

    public record IntentRequest(
            @NotBlank @Size(max = 4000) String message,
            boolean hasResume,
            boolean hasJob,
            boolean hasReport
    ) {
    }

    public record IntentResponse(
            CareerIntent intent,
            String label,
            String summary,
            double confidence,
            boolean needsWorkflow,
            List<RequiredResource> requiredResources,
            List<RequiredResource> missingResources,
            AgentNextAction nextAction,
            String reason
    ) {
    }

    public record PlanRequest(
            @NotBlank @Size(max = 4000) String message,
            Long resumeId,
            Long jobId,
            Long reportId,
            boolean executeWorkflow
    ) {
    }

    public record AgentResourceRef(
            RequiredResource type,
            Long id,
            String title
    ) {
    }

    public record PlanResponse(
            IntentResponse intent,
            List<AgentResourceRef> selectedResources,
            List<RequiredResource> missingResources,
            AgentNextAction nextAction,
            boolean canStartWorkflow,
            List<WorkflowStatus> workflowSteps,
            Long resumeId,
            Long jobId,
            Long reportId,
            CareerTaskResponse task,
            Long learningPlanId,
            Long interviewSessionId,
            CareerProfileResponse profile,
            List<String> suggestedPrompts,
            List<AgentMessageResponse> messages,
            String assistantMessage
    ) {
    }

    public record CareerProfileResponse(
            List<String> targetRoles,
            List<String> careerStages,
            List<String> weaknessTags,
            List<String> preferenceTags,
            String summary,
            List<String> suggestedPrompts
    ) {
    }

    public record AgentMessageResponse(
            Long messageId,
            AgentMessageRole role,
            AgentMessageType messageType,
            String content,
            Long taskId,
            Long reportId,
            Map<String, Object> metadata,
            Instant createdAt
    ) {
        static AgentMessageResponse from(CareerAgentMessage message) {
            return new AgentMessageResponse(
                    message.getId(),
                    message.getRole(),
                    message.getMessageType(),
                    message.getContent(),
                    message.getTaskId(),
                    message.getReportId(),
                    message.getMetadata(),
                    message.getCreatedAt()
            );
        }
    }

    public record AgentConversationResponse(
            Long conversationId,
            String title,
            CareerProfileResponse profile,
            List<AgentMessageResponse> messages,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AppendAgentMessageRequest(
            AgentMessageRole role,
            AgentMessageType messageType,
            @NotBlank @Size(max = 4000) String content,
            Long taskId,
            Long reportId,
            Map<String, Object> metadata
    ) {
    }
}
