package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FinalReportVerifier implements WorkflowStepVerifier {
    @Override
    public WorkflowStatus supports() {
        return WorkflowStatus.GENERATING_FINAL_REPORT;
    }

    @Override
    public VerificationResult verify(Long taskId, WorkflowStepResult result) {
        Map<String, Object> metrics = result.qualitySignals();
        if (result.artifactId() == null) {
            return VerificationResult.failed(NextAction.FAIL, "Final report was not created", metrics);
        }
        String reportStatus = String.valueOf(metrics.getOrDefault("reportStatus", "UNKNOWN"));
        return VerificationResult.passed("Final report generated with status " + reportStatus, metrics);
    }
}
