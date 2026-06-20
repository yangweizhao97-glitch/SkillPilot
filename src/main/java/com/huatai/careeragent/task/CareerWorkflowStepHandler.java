package com.huatai.careeragent.task;

public interface CareerWorkflowStepHandler {
    void execute(Long taskId, WorkflowStatus status);
}
