package com.huatai.careeragent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.core.AgentContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RestrictedToolCallingService {
    private final ToolRegistry registry;
    private final ToolExecutor executor;
    private final AuditDataSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public RestrictedToolCallingService(ToolRegistry registry, ToolExecutor executor,
                                        AuditDataSanitizer sanitizer, ObjectMapper objectMapper) {
        this.registry = registry;
        this.executor = executor;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
    }

    public List<RestrictedToolCallResult> execute(List<PlannedToolCall> calls, ToolCallingPolicy policy,
                                                  AgentContext context) {
        List<PlannedToolCall> planned = calls == null ? List.of() : List.copyOf(calls);
        if (planned.size() > policy.maxCalls()) {
            throw new ToolException("TOOL_CALL_LIMIT_EXCEEDED",
                    "Planned tool calls exceed maxCalls=" + policy.maxCalls(), false);
        }
        List<RestrictedToolCallResult> results = new ArrayList<>();
        for (PlannedToolCall call : planned) {
            if (!policy.allowedTools().contains(call.toolName())) {
                throw new ToolException("TOOL_NOT_ALLOWED",
                        "Agent is not allowed to plan tool: " + call.toolName(), false);
            }
            Tool<?, ?> tool = registry.getRequired(call.toolName());
            Object typedInput = objectMapper.convertValue(call.arguments(), tool.inputType());
            ToolResponse<?> response = executor.execute(new ToolRequest<>(
                    call.toolName(),
                    typedInput,
                    new ToolExecutionContext(context.userId(), context.taskId(), context.traceId(), policy.agentName())
            ));
            if (!response.success()) {
                throw new ToolException(response.error().code(), response.error().message(),
                        response.error().retryable());
            }
            results.add(new RestrictedToolCallResult(call.toolName(), sanitizer.sanitize(response.output())));
        }
        return List.copyOf(results);
    }
}
