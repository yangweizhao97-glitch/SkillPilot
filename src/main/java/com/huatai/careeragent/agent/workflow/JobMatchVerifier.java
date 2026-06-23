package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JobMatchVerifier implements WorkflowStepVerifier {
    @Override
    public WorkflowStatus supports() {
        return WorkflowStatus.MATCHING_JOB;
    }

    @Override
    public VerificationResult verify(Long taskId, WorkflowStepResult result) {
        Map<String, Object> metrics = result.qualitySignals();
        boolean hasArtifact = result.artifactId() != null;
        boolean hasScore = metrics.get("matchScore") instanceof Number;
        boolean hasSummary = Boolean.TRUE.equals(metrics.get("summaryPresent"));
        if (hasArtifact && hasScore && hasSummary) {
            return VerificationResult.passed("Job match artifact is usable", metrics);
        }
        return VerificationResult.failed(NextAction.RETRY_STEP, "Job match artifact is incomplete", metrics);
    }
}
