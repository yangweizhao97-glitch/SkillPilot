package com.huatai.careeragent.agent.handoff;

import com.huatai.careeragent.task.WorkflowStatus;

import java.util.Set;

public record HandoffRequest(
        WorkflowStatus sourceStep,
        WorkflowStatus targetStep,
        String sourceAgent,
        String targetAgent,
        String reason,
        int depth,
        Set<String> visitedAgents
) {
    public HandoffRequest {
        visitedAgents = visitedAgents == null ? Set.of() : Set.copyOf(visitedAgents);
    }
}
