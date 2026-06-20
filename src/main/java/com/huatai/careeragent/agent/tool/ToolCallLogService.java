package com.huatai.careeragent.agent.tool;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ToolCallLogService {
    private final ToolCallLogRepository repository;

    public ToolCallLogService(ToolCallLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            ToolRequest<?> request,
            Map<String, Object> input,
            Map<String, Object> output,
            ToolCallStatus status,
            long durationMs,
            String errorMessage
    ) {
        repository.save(new ToolCallLog(
                request.context(),
                request.toolName(),
                input,
                output,
                status,
                durationMs,
                errorMessage,
                request.retryCount()
        ));
    }
}
