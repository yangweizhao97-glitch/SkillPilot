package com.huatai.careeragent.agent.workflow;

import java.util.List;

public record WorkflowPlan(
        String planId,
        List<WorkflowPlanStep> steps
) {
    public WorkflowPlan {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
