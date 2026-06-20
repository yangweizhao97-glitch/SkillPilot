package com.huatai.careeragent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultCareerWorkflowStepHandler implements CareerWorkflowStepHandler {
    private static final Logger log = LoggerFactory.getLogger(DefaultCareerWorkflowStepHandler.class);

    @Override
    public void execute(Long taskId, WorkflowStatus status) {
        log.info("Career workflow step completed: taskId={}, status={}", taskId, status);
    }
}
