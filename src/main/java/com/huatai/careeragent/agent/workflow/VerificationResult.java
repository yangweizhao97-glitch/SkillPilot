package com.huatai.careeragent.agent.workflow;

import java.util.Map;

public record VerificationResult(
        boolean passed,
        NextAction nextAction,
        String reason,
        Map<String, Object> metrics
) {
    public VerificationResult {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    public static VerificationResult passed(String reason, Map<String, Object> metrics) {
        return new VerificationResult(true, NextAction.CONTINUE, reason, metrics);
    }

    public static VerificationResult failed(NextAction nextAction, String reason, Map<String, Object> metrics) {
        return new VerificationResult(false, nextAction, reason, metrics);
    }
}
