package com.huatai.careeragent.agent.workflow;

public interface WorkflowRouter {
    RouteDecision decide(WorkflowRuntime runtime, VerificationResult verification);
}
