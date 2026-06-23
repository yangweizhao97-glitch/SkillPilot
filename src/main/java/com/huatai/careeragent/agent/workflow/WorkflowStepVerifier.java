package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.WorkflowStatus;

public interface WorkflowStepVerifier {
    WorkflowStatus supports();
    VerificationResult verify(Long taskId, WorkflowStepResult result);
}
