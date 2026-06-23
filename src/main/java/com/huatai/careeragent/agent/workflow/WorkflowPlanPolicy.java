package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WorkflowPlanPolicy {
    public List<WorkflowStatus> validateRemotePlan(String runId, List<String> plannedStatuses, AgentTask task) {
        if (runId == null || runId.isBlank() || plannedStatuses == null) {
            throw new IllegalArgumentException("LangGraph returned an incomplete workflow plan");
        }
        List<WorkflowStatus> statuses;
        try {
            statuses = plannedStatuses.stream().map(WorkflowStatus::valueOf).toList();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("LangGraph returned an unknown workflow status", exception);
        }
        if (!isLegalSubsequence(statuses)) {
            throw new IllegalArgumentException("LangGraph plan violates the career workflow ordering contract");
        }
        List<WorkflowStatus> required = requiredStatuses(task);
        if (!statuses.containsAll(required)) {
            throw new IllegalArgumentException("LangGraph plan skipped a required career workflow step");
        }
        return statuses;
    }

    public boolean isLegalSubsequence(List<WorkflowStatus> statuses) {
        if (statuses.isEmpty() || statuses.getLast() != WorkflowStatus.SUCCESS
                || statuses.stream().distinct().count() != statuses.size()) {
            return false;
        }
        int cursor = -1;
        for (WorkflowStatus status : statuses) {
            int index = CareerWorkflowRunner.EXECUTION_ORDER.indexOf(status);
            if (index < 0 || index <= cursor) {
                return false;
            }
            cursor = index;
        }
        return true;
    }

    public List<WorkflowStatus> requiredStatuses(AgentTask task) {
        ArrayList<WorkflowStatus> required = new ArrayList<>();
        required.addAll(task.getEnabledSteps());
        if (task.getJobId() != null) {
            required.add(WorkflowStatus.GENERATING_FINAL_REPORT);
        }
        required.add(WorkflowStatus.SUCCESS);
        return List.copyOf(required);
    }
}
