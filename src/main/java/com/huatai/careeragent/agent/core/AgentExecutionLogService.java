package com.huatai.careeragent.agent.core;

import com.huatai.careeragent.llm.LlmResponse.TokenUsage;
import com.huatai.careeragent.task.log.AgentExecutionLog;
import com.huatai.careeragent.task.log.AgentExecutionLogRepository;
import com.huatai.careeragent.task.log.ExecutionLogStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionLogService {
    private final AgentExecutionLogRepository repository;

    public AgentExecutionLogService(AgentExecutionLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AgentContext context,
            String agentName,
            String stepName,
            String inputSummary,
            String outputSummary,
            ExecutionLogStatus status,
            long durationMs,
            TokenUsage usage,
            String errorMessage
    ) {
        repository.save(AgentExecutionLog.agentExecution(
                context.userId(), context.taskId(), context.traceId(), agentName, stepName,
                inputSummary, outputSummary, status, durationMs, usage, errorMessage
        ));
    }
}
