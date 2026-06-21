package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.CareerTaskStateService;
import com.huatai.careeragent.task.CareerWorkflowStepHandler;
import com.huatai.careeragent.task.WorkflowStatus;
import com.huatai.careeragent.agent.handoff.HandoffCoordinator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private final HandoffCoordinator handoffCoordinator;

    public CareerWorkflowRunner(CareerTaskStateService stateService, CareerWorkflowStepHandler stepHandler,
                                HandoffCoordinator handoffCoordinator) {
        this.stateService = stateService;
        this.stepHandler = stepHandler;
        this.handoffCoordinator = handoffCoordinator;
    }

    public void run(Long taskId, List<WorkflowStatus> executionOrder) {
        List<WorkflowStatus> activeAgentSteps = handoffCoordinator.activeAgentSteps(taskId, executionOrder);
        Set<String> visitedAgents = new LinkedHashSet<>();
        int handoffDepth = 0;
        for (WorkflowStatus status : executionOrder) {
            stateService.transition(taskId, status);
            if (status != WorkflowStatus.SUCCESS) {
                long started = System.nanoTime();
                stepHandler.execute(taskId, status);
                if (status == WorkflowStatus.GENERATING_FINAL_REPORT) {
                    stateService.completeStep(taskId, status, (System.nanoTime() - started) / 1_000_000);
                }
                int activeIndex = activeAgentSteps.indexOf(status);
                if (activeIndex >= 0) {
                    visitedAgents.add(handoffCoordinator.agentFor(status));
                    if (activeIndex + 1 < activeAgentSteps.size()) {
                        handoffCoordinator.handoff(taskId, status, activeAgentSteps.get(activeIndex + 1),
                                ++handoffDepth, visitedAgents);
                    }
                }
            }
        }
    }
}
