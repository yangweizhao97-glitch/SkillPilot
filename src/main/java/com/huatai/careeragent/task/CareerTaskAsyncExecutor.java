package com.huatai.careeragent.task;

import com.huatai.careeragent.agent.workflow.AgentWorkflowExecutor;
import com.huatai.careeragent.common.trace.TraceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class CareerTaskAsyncExecutor {
    private static final Logger log = LoggerFactory.getLogger(CareerTaskAsyncExecutor.class);
    private final CareerTaskStateService stateService;
    private final AgentWorkflowExecutor workflowExecutor;

    public CareerTaskAsyncExecutor(CareerTaskStateService stateService, AgentWorkflowExecutor workflowExecutor) {
        this.stateService = stateService;
        this.workflowExecutor = workflowExecutor;
    }

    @Async("careerTaskExecutor")
    public CompletableFuture<Void> execute(Long taskId, String traceId) {
        TraceIdContext.set(traceId);
        try {
            workflowExecutor.execute(taskId);
            log.info("Career task completed: taskId={}, engine={}", taskId, workflowExecutor.engine());
            return CompletableFuture.completedFuture(null);
        } catch (Exception exception) {
            log.error("Career task failed: taskId={}, reason={}", taskId, exception.getMessage(), exception);
            try {
                stateService.fail(taskId, exception.getMessage());
            } catch (Exception stateException) {
                log.error("Failed to persist career task failure: taskId={}", taskId, stateException);
            }
            return CompletableFuture.failedFuture(exception);
        } finally {
            TraceIdContext.clear();
        }
    }
}
