package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.WorkflowStatus;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class WorkflowRuntime {
    private final Long taskId;
    private WorkflowPlan plan;
    private final Map<WorkflowStatus, WorkflowStepResult> artifacts = new EnumMap<>(WorkflowStatus.class);
    private final Map<WorkflowStatus, VerificationResult> verifications = new EnumMap<>(WorkflowStatus.class);
    private final Map<WorkflowStatus, Integer> attempts = new EnumMap<>(WorkflowStatus.class);
    private final Set<String> visitedAgents = new LinkedHashSet<>();
    private int cursor;
    private int handoffDepth;

    public WorkflowRuntime(Long taskId, WorkflowPlan plan) {
        this.taskId = taskId;
        this.plan = plan;
    }

    public Long taskId() {
        return taskId;
    }

    public WorkflowPlan plan() {
        return plan;
    }

    public boolean hasNext() {
        return cursor < plan.steps().size();
    }

    public WorkflowPlanStep currentStep() {
        return plan.steps().get(cursor);
    }

    public Optional<WorkflowPlanStep> nextStep() {
        int next = cursor + 1;
        return next < plan.steps().size() ? Optional.of(plan.steps().get(next)) : Optional.empty();
    }

    public int currentAttempt() {
        return attempts.getOrDefault(currentStep().status(), 0) + 1;
    }

    public void recordAttempt(WorkflowStepResult result, VerificationResult verification) {
        WorkflowStatus status = currentStep().status();
        attempts.put(status, currentAttempt());
        artifacts.put(status, result);
        verifications.put(status, verification);
    }

    public int attempts(WorkflowStatus status) {
        return attempts.getOrDefault(status, 0);
    }

    public void replaceCurrentAndRemainingPlan(WorkflowStatus currentStatus, WorkflowPlan candidate) {
        int currentIndex = -1;
        for (int index = 0; index < candidate.steps().size(); index++) {
            if (candidate.steps().get(index).status() == currentStatus) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) {
            throw new IllegalArgumentException("Replanned workflow must include the current step: " + currentStatus);
        }
        List<WorkflowPlanStep> remaining = candidate.steps().subList(currentIndex, candidate.steps().size());
        this.plan = new WorkflowPlan(candidate.planId(), remaining);
        this.cursor = 0;
    }

    public void advance() {
        cursor++;
    }

    public Set<String> visitedAgents() {
        return visitedAgents;
    }

    public void visit(String agentName) {
        if (agentName != null) {
            visitedAgents.add(agentName);
        }
    }

    public int nextHandoffDepth() {
        handoffDepth++;
        return handoffDepth;
    }

    public Map<String, Object> snapshotMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("planId", plan.planId());
        metrics.put("cursor", cursor);
        metrics.put("attempts", Map.copyOf(attempts));
        metrics.put("artifacts", Map.copyOf(artifacts));
        metrics.put("verifications", Map.copyOf(verifications));
        return metrics;
    }
}
