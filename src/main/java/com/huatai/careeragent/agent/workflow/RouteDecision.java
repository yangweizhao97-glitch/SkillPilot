package com.huatai.careeragent.agent.workflow;

public record RouteDecision(
        NextAction action,
        String reason
) {
    public static RouteDecision continueWorkflow(String reason) {
        return new RouteDecision(NextAction.CONTINUE, reason);
    }

    public static RouteDecision retry(String reason) {
        return new RouteDecision(NextAction.RETRY_STEP, reason);
    }

    public static RouteDecision skip(String reason) {
        return new RouteDecision(NextAction.SKIP_STEP, reason);
    }

    public static RouteDecision fail(String reason) {
        return new RouteDecision(NextAction.FAIL, reason);
    }
}
