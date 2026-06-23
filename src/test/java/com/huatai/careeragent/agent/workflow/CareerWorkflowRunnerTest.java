package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.agent.handoff.HandoffCoordinator;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.CareerTaskStateService;
import com.huatai.careeragent.task.CareerWorkflowStepHandler;
import com.huatai.careeragent.task.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerWorkflowRunnerTest {
    @Test
    void appliesReplannedPlanAndSkipsRemovedOptionalFutureStep() {
        CareerTaskStateService stateService = mock(CareerTaskStateService.class);
        AgentTaskRepository taskRepository = mock(AgentTaskRepository.class);
        CareerWorkflowStepHandler stepHandler = mock(CareerWorkflowStepHandler.class);
        HandoffCoordinator handoffCoordinator = mock(HandoffCoordinator.class);
        WorkflowStepVerifierRegistry verifierRegistry = mock(WorkflowStepVerifierRegistry.class);
        WorkflowReplanner workflowReplanner = mock(WorkflowReplanner.class);
        CareerWorkflowRunner runner = new CareerWorkflowRunner(
                stateService,
                taskRepository,
                stepHandler,
                handoffCoordinator,
                verifierRegistry,
                new DefaultWorkflowRouter(),
                workflowReplanner
        );
        AgentTask task = new AgentTask(7L, "trace-42", 10L, 20L,
                List.of(WorkflowStatus.ANALYZING_RESUME, WorkflowStatus.GENERATING_QUESTIONS),
                List.of(WorkflowStatus.GENERATING_QUESTIONS));
        when(taskRepository.findById(42L)).thenReturn(Optional.of(task));
        when(handoffCoordinator.activeAgentSteps(42L, CareerWorkflowRunner.EXECUTION_ORDER))
                .thenReturn(List.of(WorkflowStatus.ANALYZING_RESUME, WorkflowStatus.GENERATING_QUESTIONS,
                        WorkflowStatus.GENERATING_FINAL_REPORT));
        when(handoffCoordinator.agentFor(WorkflowStatus.ANALYZING_RESUME))
                .thenReturn(AgentNames.RESUME_ANALYSIS_AGENT);
        when(handoffCoordinator.agentFor(WorkflowStatus.GENERATING_QUESTIONS))
                .thenReturn(AgentNames.INTERVIEW_QUESTION_AGENT);
        when(handoffCoordinator.agentFor(WorkflowStatus.GENERATING_FINAL_REPORT))
                .thenReturn(AgentNames.FINAL_REPORT_AGENT);

        WorkflowStepResult incompleteAnalysis = result(WorkflowStatus.ANALYZING_RESUME,
                AgentNames.RESUME_ANALYSIS_AGENT, "RESUME_ANALYSIS_REPORT", Map.of("summaryPresent", false));
        WorkflowStepResult completeAnalysis = result(WorkflowStatus.ANALYZING_RESUME,
                AgentNames.RESUME_ANALYSIS_AGENT, "RESUME_ANALYSIS_REPORT", Map.of("summaryPresent", true));
        WorkflowStepResult finalReport = result(WorkflowStatus.GENERATING_FINAL_REPORT,
                AgentNames.FINAL_REPORT_AGENT, "FINAL_REPORT", Map.of("ready", true));
        VerificationResult replanRequired = VerificationResult.failed(NextAction.REPLAN,
                "analysis artifact missing summary", Map.of("summaryPresent", false));
        VerificationResult passed = VerificationResult.passed("artifact accepted", Map.of());
        WorkflowPlan replanned = new WorkflowPlan("replan-42", List.of(
                new WorkflowPlanStep(WorkflowStatus.ANALYZING_RESUME, AgentNames.RESUME_ANALYSIS_AGENT, true, 2),
                new WorkflowPlanStep(WorkflowStatus.GENERATING_FINAL_REPORT, AgentNames.FINAL_REPORT_AGENT, true, 2)
        ));

        when(stepHandler.execute(42L, WorkflowStatus.ANALYZING_RESUME))
                .thenReturn(incompleteAnalysis, completeAnalysis);
        when(stepHandler.execute(42L, WorkflowStatus.GENERATING_FINAL_REPORT))
                .thenReturn(finalReport);
        when(verifierRegistry.verify(42L, incompleteAnalysis)).thenReturn(replanRequired);
        when(verifierRegistry.verify(42L, completeAnalysis)).thenReturn(passed);
        when(verifierRegistry.verify(42L, finalReport)).thenReturn(passed);
        when(workflowReplanner.replan(any(WorkflowRuntime.class), eq(replanRequired))).thenReturn(replanned);

        runner.run(42L, CareerWorkflowRunner.EXECUTION_ORDER);

        verify(stepHandler, times(2)).execute(42L, WorkflowStatus.ANALYZING_RESUME);
        verify(stepHandler, never()).execute(42L, WorkflowStatus.GENERATING_QUESTIONS);
        verify(stepHandler).execute(42L, WorkflowStatus.GENERATING_FINAL_REPORT);
        verify(handoffCoordinator).handoff(eq(42L), eq(WorkflowStatus.ANALYZING_RESUME),
                eq(WorkflowStatus.GENERATING_FINAL_REPORT), eq(1), any());
        verify(stateService, never()).transition(42L, WorkflowStatus.GENERATING_QUESTIONS);
        verify(stateService).transition(42L, WorkflowStatus.SUCCESS);
    }

    private WorkflowStepResult result(WorkflowStatus status, String agentName, String artifactType,
                                      Map<String, Object> qualitySignals) {
        return new WorkflowStepResult(status, agentName, artifactType, 1L, qualitySignals);
    }
}
