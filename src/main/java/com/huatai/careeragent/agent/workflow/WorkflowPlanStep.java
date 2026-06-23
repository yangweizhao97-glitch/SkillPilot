package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.WorkflowStatus;

public record WorkflowPlanStep(
        WorkflowStatus status,
        String agentName,
        boolean required,
        int maxAttempts
) {
    public WorkflowPlanStep {
        if (maxAttempts < 1) {
            maxAttempts = 1;
        }
    }
}
