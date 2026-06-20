package com.huatai.careeragent.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareerTaskStateService {
    private final AgentTaskRepository agentTaskRepository;

    public CareerTaskStateService(AgentTaskRepository agentTaskRepository) {
        this.agentTaskRepository = agentTaskRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transition(Long taskId, WorkflowStatus next) {
        AgentTask task = agentTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        task.transitionTo(next);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long taskId, String errorMessage) {
        AgentTask task = agentTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        task.fail(errorMessage);
    }
}
