package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowStepVerifierRegistry {
    private final Map<WorkflowStatus, WorkflowStepVerifier> verifiers;

    public WorkflowStepVerifierRegistry(List<WorkflowStepVerifier> verifiers) {
        Map<WorkflowStatus, WorkflowStepVerifier> values = new EnumMap<>(WorkflowStatus.class);
        verifiers.forEach(verifier -> values.put(verifier.supports(), verifier));
        this.verifiers = Map.copyOf(values);
    }

    public VerificationResult verify(Long taskId, WorkflowStepResult result) {
        WorkflowStepVerifier verifier = verifiers.get(result.step());
        if (verifier == null) {
            return VerificationResult.passed("No verifier registered for " + result.step(), result.qualitySignals());
        }
        return verifier.verify(taskId, result);
    }
}
