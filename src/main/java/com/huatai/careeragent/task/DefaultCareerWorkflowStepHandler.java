package com.huatai.careeragent.task;

import com.huatai.careeragent.agent.workflow.CareerWorkflowService;
import org.springframework.stereotype.Component;

@Component
public class DefaultCareerWorkflowStepHandler implements CareerWorkflowStepHandler {
    private final CareerWorkflowService workflowService;

    public DefaultCareerWorkflowStepHandler(CareerWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public void execute(Long taskId, WorkflowStatus status) {
        workflowService.executeStep(taskId, status);
    }
}
