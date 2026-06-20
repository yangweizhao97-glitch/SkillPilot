package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.CareerTaskStateService;
import com.huatai.careeragent.task.CareerWorkflowStepHandler;
import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CareerWorkflowRunner {
    public static final List<WorkflowStatus> EXECUTION_ORDER = List.of(
            WorkflowStatus.MATCHING_JOB,
            WorkflowStatus.ANALYZING_RESUME,
            WorkflowStatus.GENERATING_QUESTIONS,
            WorkflowStatus.GENERATING_FINAL_REPORT,
            WorkflowStatus.SUCCESS
    );

    private final CareerTaskStateService stateService;
    private final CareerWorkflowStepHandler stepHandler;

    public CareerWorkflowRunner(CareerTaskStateService stateService, CareerWorkflowStepHandler stepHandler) {
        this.stateService = stateService;
        this.stepHandler = stepHandler;
    }

    public void run(Long taskId, List<WorkflowStatus> executionOrder) {
        for (WorkflowStatus status : executionOrder) {
            stateService.transition(taskId, status);
            if (status != WorkflowStatus.SUCCESS) {
                long started = System.nanoTime();
                stepHandler.execute(taskId, status);
                if (status == WorkflowStatus.GENERATING_FINAL_REPORT) {
                    stateService.completeStep(taskId, status, (System.nanoTime() - started) / 1_000_000);
                }
            }
        }
    }
}
