package com.huatai.careeragent.agent.agents;

import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.tool.ToolCallLogService;
import com.huatai.careeragent.agent.tool.ToolCallStatus;
import com.huatai.careeragent.agent.tool.ToolExecutionContext;
import com.huatai.careeragent.agent.tool.ToolRequest;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentLlmGateway {
    private final LlmClient client;
    private final ToolCallLogService logs;

    public AgentLlmGateway(LlmClient client, ToolCallLogService logs) {
        this.client = client;
        this.logs = logs;
    }

    public LlmResponse complete(LlmRequest request, AgentContext context, String agentName) {
        Map<String, Object> summary = Map.of(
                "model", request.model() == null ? "default" : request.model(),
                "messageCount", request.messages().size(), "jsonMode", request.jsonMode());
        ToolRequest<Map<String, Object>> call = new ToolRequest<>("LLM", summary,
                new ToolExecutionContext(context.userId(), context.taskId(), context.traceId(), agentName));
        String callId = logs.start(call, summary);
        long started = System.nanoTime();
        try {
            LlmResponse response = client.complete(request);
            logs.complete(callId, Map.of("provider", response.provider(), "model", response.model(),
                            "totalTokens", response.usage().totalTokens()),
                    ToolCallStatus.TOOL_COMPLETED, elapsed(started), null);
            return response;
        } catch (RuntimeException exception) {
            logs.complete(callId, null, ToolCallStatus.TOOL_FAILED, elapsed(started), exception.getMessage());
            throw exception;
        }
    }

    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
