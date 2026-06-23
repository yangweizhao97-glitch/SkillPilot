package com.huatai.careeragent.task;

import com.huatai.careeragent.task.log.AgentExecutionLog;
import com.huatai.careeragent.task.log.AgentExecutionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareerTaskStateService {
    private final AgentTaskRepository agentTaskRepository;
    private final AgentExecutionLogRepository executionLogRepository;

    public CareerTaskStateService(
            AgentTaskRepository agentTaskRepository,
            AgentExecutionLogRepository executionLogRepository
    ) {
        this.agentTaskRepository = agentTaskRepository;
        this.executionLogRepository = executionLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transition(Long taskId, WorkflowStatus next) {
        AgentTask task = agentTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        task.transitionTo(next);
        executionLogRepository.save(AgentExecutionLog.transition(task, next));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long taskId, String errorMessage) {
        AgentTask task = agentTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        WorkflowStatus failedStep = task.getStatus();
        task.fail(errorMessage);
        executionLogRepository.save(AgentExecutionLog.failure(task, failedStep, errorMessage));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeStep(Long taskId, WorkflowStatus status, long durationMs) {
        AgentTask task = agentTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        executionLogRepository.save(AgentExecutionLog.completed(task, status, durationMs));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordWorkflowEvent(Long taskId, WorkflowStatus status, String agentName,
                                    com.huatai.careeragent.task.log.ExecutionLogStatus eventStatus,
                                    String outputSummary, long durationMs, String errorMessage) {
        AgentTask task = agentTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        executionLogRepository.save(AgentExecutionLog.workflowEvent(
                task, status, agentName, eventStatus, outputSummary, durationMs, errorMessage
        ));
    }
}
