package com.huatai.careeragent.agent.workflow;

public interface WorkflowReplanner {
    WorkflowPlan replan(WorkflowRuntime runtime, VerificationResult verification);
}
