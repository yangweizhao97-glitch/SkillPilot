package com.huatai.careeragent.task;

import com.huatai.careeragent.common.trace.TraceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class CareerTaskAsyncExecutor {
    private static final Logger log = LoggerFactory.getLogger(CareerTaskAsyncExecutor.class);
    private static final List<WorkflowStatus> EXECUTION_ORDER = List.of(
            WorkflowStatus.MATCHING_JOB,
            WorkflowStatus.ANALYZING_RESUME,
            WorkflowStatus.GENERATING_QUESTIONS,
            WorkflowStatus.GENERATING_FINAL_REPORT,
            WorkflowStatus.SUCCESS
    );

    private final CareerTaskStateService stateService;
    private final CareerWorkflowStepHandler stepHandler;

    public CareerTaskAsyncExecutor(CareerTaskStateService stateService, CareerWorkflowStepHandler stepHandler) {
        this.stateService = stateService;
        this.stepHandler = stepHandler;
    }

    @Async("careerTaskExecutor")
    public CompletableFuture<Void> execute(Long taskId, String traceId) {
        TraceIdContext.set(traceId);
        try {
            for (WorkflowStatus status : EXECUTION_ORDER) {
                stateService.transition(taskId, status);
                if (status != WorkflowStatus.SUCCESS) {
                    long started = System.nanoTime();
                    stepHandler.execute(taskId, status);
                    if (status == WorkflowStatus.GENERATING_FINAL_REPORT) {
                        stateService.completeStep(taskId, status, (System.nanoTime() - started) / 1_000_000);
                    }
                }
            }
            log.info("Career task completed: taskId={}", taskId);
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
