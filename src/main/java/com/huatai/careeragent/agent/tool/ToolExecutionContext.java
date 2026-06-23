package com.huatai.careeragent.agent.tool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ToolExecutionContext(
        @NotNull Long userId,
        Long taskId,
        @NotNull ToolScopeType scopeType,
        @NotNull Long scopeId,
        @NotBlank String traceId,
        @NotBlank String agentName
) {
    public ToolExecutionContext(Long userId, Long taskId, String traceId, String agentName) {
        this(userId, taskId, ToolScopeType.TASK, taskId, traceId, agentName);
    }

    public static ToolExecutionContext tutorSession(Long userId, Long sessionId, String traceId, String agentName) {
        return new ToolExecutionContext(userId, null, ToolScopeType.TUTOR_SESSION, sessionId, traceId, agentName);
    }

    public static ToolExecutionContext interviewSession(Long userId, Long sessionId, String traceId,
                                                        String agentName) {
        return new ToolExecutionContext(userId, null, ToolScopeType.INTERVIEW_SESSION, sessionId, traceId, agentName);
    }
}
