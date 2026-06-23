package com.huatai.careeragent.task;

import com.huatai.careeragent.agent.workflow.WorkflowStepResult;

public interface CareerWorkflowStepHandler {
    WorkflowStepResult execute(Long taskId, WorkflowStatus status);
}
