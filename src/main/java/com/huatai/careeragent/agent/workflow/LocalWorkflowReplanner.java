package com.huatai.careeragent.agent.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "career-agent.workflow.engine", havingValue = "spring", matchIfMissing = true)
public class LocalWorkflowReplanner implements WorkflowReplanner {
    @Override
    public WorkflowPlan replan(WorkflowRuntime runtime, VerificationResult verification) {
        throw new IllegalStateException("Local workflow replanning is not enabled: " + verification.reason());
    }
}
