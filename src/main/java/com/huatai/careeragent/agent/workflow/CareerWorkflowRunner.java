package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.CareerTaskStateService;
import com.huatai.careeragent.task.CareerWorkflowStepHandler;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import com.huatai.careeragent.agent.handoff.HandoffCoordinator;
import com.huatai.careeragent.task.log.ExecutionLogStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CareerWorkflowRunner {
    private static final int MAX_VERIFICATION_ATTEMPTS = 2;

    public static final List<WorkflowStatus> EXECUTION_ORDER = List.of(
            WorkflowStatus.MATCHING_JOB,
            WorkflowStatus.ANALYZING_RESUME,
            WorkflowStatus.GENERATING_QUESTIONS,
            WorkflowStatus.GENERATING_FINAL_REPORT,
            WorkflowStatus.SUCCESS
    );

    private final CareerTaskStateService stateService;
    private final AgentTaskRepository taskRepository;
    private final CareerWorkflowStepHandler stepHandler;
    private final HandoffCoordinator handoffCoordinator;
    private final WorkflowStepVerifierRegistry verifierRegistry;
    private final WorkflowRouter workflowRouter;
    private final WorkflowReplanner workflowReplanner;

    public CareerWorkflowRunner(CareerTaskStateService stateService,
                                AgentTaskRepository taskRepository,
                                CareerWorkflowStepHandler stepHandler,
                                HandoffCoordinator handoffCoordinator,
                                WorkflowStepVerifierRegistry verifierRegistry,
                                WorkflowRouter workflowRouter,
                                WorkflowReplanner workflowReplanner) {
        this.stateService = stateService;
        this.taskRepository = taskRepository;
        this.stepHandler = stepHandler;
        this.handoffCoordinator = handoffCoordinator;
        this.verifierRegistry = verifierRegistry;
        this.workflowRouter = workflowRouter;
        this.workflowReplanner = workflowReplanner;
    }

    public void run(Long taskId, List<WorkflowStatus> executionOrder) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        List<WorkflowStatus> activeAgentSteps = handoffCoordinator.activeAgentSteps(taskId, executionOrder);
        WorkflowRuntime runtime = new WorkflowRuntime(taskId, plan(activeAgentSteps, task.getOptionalSteps()));
        while (runtime.hasNext()) {
            WorkflowPlanStep current = runtime.currentStep();
            WorkflowStatus status = current.status();
            stateService.transition(taskId, status);
            RouteDecision decision = executeRouted(runtime);
            if (decision.action() == NextAction.FAIL) {
                throw new IllegalStateException(decision.reason());
            }
            if (decision.action() != NextAction.SKIP_STEP) {
                runtime.visit(current.agentName());
            } else {
                recordSkip(taskId, status, current.agentName(), decision);
            }
            if (decision.action() == NextAction.CONTINUE && runtime.nextStep().isPresent()) {
                handoffCoordinator.handoff(taskId, status, runtime.nextStep().get().status(),
                        runtime.nextHandoffDepth(), runtime.visitedAgents());
            }
            runtime.advance();
        }
        stateService.transition(taskId, WorkflowStatus.SUCCESS);
    }

    private WorkflowPlan plan(List<WorkflowStatus> activeAgentSteps, List<WorkflowStatus> optionalSteps) {
        List<WorkflowPlanStep> steps = activeAgentSteps.stream()
                .map(status -> new WorkflowPlanStep(status, handoffCoordinator.agentFor(status),
                        !optionalSteps.contains(status), MAX_VERIFICATION_ATTEMPTS))
                .toList();
        return new WorkflowPlan("spring-" + UUID.randomUUID(), steps);
    }

    private RouteDecision executeRouted(WorkflowRuntime runtime) {
        while (true) {
            WorkflowPlanStep current = runtime.currentStep();
            int attempt = runtime.currentAttempt();
            long started = System.nanoTime();
            WorkflowStepResult result = stepHandler.execute(runtime.taskId(), current.status());
            long durationMs = elapsedMs(started);
            recordArtifact(runtime.taskId(), current.status(), result, attempt, durationMs);
            VerificationResult verification = verifierRegistry.verify(runtime.taskId(), result);
            runtime.recordAttempt(result, verification);
            recordVerification(runtime.taskId(), current.status(), result, verification, attempt);
            RouteDecision decision = workflowRouter.decide(runtime, verification);
            recordRouteDecision(runtime.taskId(), current.status(), result, decision, attempt);
            if (decision.action() == NextAction.RETRY_STEP) {
                recordRetry(runtime.taskId(), current.status(), result, verification, attempt + 1);
                continue;
            }
            if (decision.action() == NextAction.REPLAN) {
                WorkflowPlan replanned = workflowReplanner.replan(runtime, verification);
                applyReplan(runtime, current, replanned, result, verification);
                continue;
            }
            return decision;
        }
    }

    private void applyReplan(WorkflowRuntime runtime, WorkflowPlanStep current, WorkflowPlan replanned,
                             WorkflowStepResult result, VerificationResult verification) {
        if (runtime.attempts(current.status()) >= current.maxAttempts()) {
            throw new IllegalStateException("Required workflow step exhausted verification attempts before replan "
                    + "could be applied: " + current.status());
        }
        runtime.replaceCurrentAndRemainingPlan(current.status(), replanned);
        recordRetry(runtime.taskId(), current.status(), result,
                VerificationResult.failed(NextAction.REPLAN,
                        verification.reason() + "; appliedPlan=" + replanned.planId(),
                        verification.metrics()),
                runtime.attempts(current.status()) + 1);
    }

    private void recordArtifact(Long taskId, WorkflowStatus status, WorkflowStepResult result, int attempt,
                                long durationMs) {
        stateService.recordWorkflowEvent(taskId, status, result.agentName(), ExecutionLogStatus.STEP_COMPLETED,
                "Artifact recorded: type=" + result.artifactType()
                        + ", artifactId=" + result.artifactId()
                        + ", attempt=" + attempt
                        + ", qualitySignals=" + result.qualitySignals(),
                durationMs, null);
    }

    private void recordVerification(Long taskId, WorkflowStatus status, WorkflowStepResult result,
                                    VerificationResult verification, int attempt) {
        ExecutionLogStatus eventStatus = verification.passed()
                ? ExecutionLogStatus.VERIFICATION_PASSED : ExecutionLogStatus.VERIFICATION_FAILED;
        stateService.recordWorkflowEvent(taskId, status, result.agentName(), eventStatus,
                "Verifier result: passed=" + verification.passed()
                        + ", action=" + verification.nextAction()
                        + ", attempt=" + attempt
                        + ", reason=" + verification.reason()
                        + ", metrics=" + verification.metrics(),
                0, verification.passed() ? null : verification.reason());
    }

    private void recordRouteDecision(Long taskId, WorkflowStatus status, WorkflowStepResult result,
                                     RouteDecision decision, int attempt) {
        stateService.recordWorkflowEvent(taskId, status, result.agentName(), ExecutionLogStatus.ROUTE_DECIDED,
                "Route decision: action=" + decision.action()
                        + ", attempt=" + attempt
                        + ", reason=" + decision.reason(),
                0, decision.action() == NextAction.FAIL ? decision.reason() : null);
    }

    private void recordRetry(Long taskId, WorkflowStatus status, WorkflowStepResult result,
                             VerificationResult verification, int nextAttempt) {
        stateService.recordWorkflowEvent(taskId, status, result.agentName(), ExecutionLogStatus.STEP_RETRYING,
                "Retrying workflow step: nextAttempt=" + nextAttempt
                        + ", reason=" + verification.reason(),
                0, null);
    }

    private void recordSkip(Long taskId, WorkflowStatus status, String agentName, RouteDecision decision) {
        stateService.recordWorkflowEvent(taskId, status, agentName, ExecutionLogStatus.STEP_SKIPPED,
                "Workflow step skipped: reason=" + decision.reason(), 0, null);
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
