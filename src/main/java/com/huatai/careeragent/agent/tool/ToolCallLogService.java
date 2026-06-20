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
    public String start(
            ToolRequest<?> request,
            Map<String, Object> input
    ) {
        ToolCallLog log = repository.save(new ToolCallLog(
                request.context(), request.toolName(), input, null,
                ToolCallStatus.TOOL_STARTED, 0, null, request.retryCount()
        ));
        return log.getToolCallId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            String toolCallId,
            Map<String, Object> output,
            ToolCallStatus status,
            long durationMs,
            String errorMessage
    ) {
        ToolCallLog log = repository.findByToolCallId(toolCallId)
                .orElseThrow(() -> new IllegalStateException("Tool call not found: " + toolCallId));
        log.complete(output, status, durationMs, errorMessage);
    }
}
