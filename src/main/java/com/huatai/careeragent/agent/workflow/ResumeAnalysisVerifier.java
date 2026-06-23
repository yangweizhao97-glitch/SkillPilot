package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ResumeAnalysisVerifier implements WorkflowStepVerifier {
    @Override
    public WorkflowStatus supports() {
        return WorkflowStatus.ANALYZING_RESUME;
    }

    @Override
    public VerificationResult verify(Long taskId, WorkflowStepResult result) {
        Map<String, Object> metrics = result.qualitySignals();
        boolean hasArtifact = result.artifactId() != null;
        boolean hasSummary = Boolean.TRUE.equals(metrics.get("summaryPresent"));
        int actionableItems = number(metrics.get("suggestionCount")) + number(metrics.get("nextActionCount"));
        if (hasArtifact && hasSummary && actionableItems > 0) {
            return VerificationResult.passed("Resume analysis artifact is usable", metrics);
        }
        return VerificationResult.failed(NextAction.RETRY_STEP, "Resume analysis artifact is incomplete", metrics);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
