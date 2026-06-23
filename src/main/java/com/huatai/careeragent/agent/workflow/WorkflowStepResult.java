package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.WorkflowStatus;

import java.util.Map;

public record WorkflowStepResult(
        WorkflowStatus step,
        String agentName,
        String artifactType,
        Long artifactId,
        Map<String, Object> qualitySignals
) {
    public WorkflowStepResult {
        qualitySignals = qualitySignals == null ? Map.of() : Map.copyOf(qualitySignals);
    }
}
