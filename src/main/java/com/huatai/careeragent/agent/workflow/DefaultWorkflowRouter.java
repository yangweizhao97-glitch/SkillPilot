package com.huatai.careeragent.agent.workflow;

import org.springframework.stereotype.Component;

@Component
public class DefaultWorkflowRouter implements WorkflowRouter {
    @Override
    public RouteDecision decide(WorkflowRuntime runtime, VerificationResult verification) {
        WorkflowPlanStep step = runtime.currentStep();
        if (verification.passed() || verification.nextAction() == NextAction.CONTINUE) {
            return RouteDecision.continueWorkflow("Verifier allowed continuation");
        }
        if (verification.nextAction() == NextAction.SKIP_STEP && !step.required()) {
            return RouteDecision.skip("Optional step skipped: " + verification.reason());
        }
        if (verification.nextAction() == NextAction.RETRY_STEP
                && runtime.attempts(step.status()) < step.maxAttempts()) {
            return RouteDecision.retry("Retry allowed: " + verification.reason());
        }
        if (verification.nextAction() == NextAction.REPLAN) {
            return RouteDecision.fail("Replan is not enabled in the local router yet: " + verification.reason());
        }
        return RouteDecision.fail("Required workflow step failed verification: " + verification.reason());
    }
}
