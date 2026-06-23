package com.huatai.careeragent.agent.core;

import com.huatai.careeragent.llm.LlmResponse.TokenUsage;

import java.util.Map;

public record AgentResult<O>(O output, String outputSummary, TokenUsage usage, Map<String, Object> metadata) {
    public AgentResult {
        usage = usage == null ? TokenUsage.empty() : usage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public AgentResult(O output, String outputSummary, TokenUsage usage) {
        this(output, outputSummary, usage, Map.of());
    }

    public static <O> AgentResult<O> success(O output, String outputSummary, TokenUsage usage) {
        return new AgentResult<>(output, outputSummary, usage, Map.of());
    }

    public static <O> AgentResult<O> success(O output, String outputSummary, TokenUsage usage,
                                             Map<String, Object> metadata) {
        return new AgentResult<>(output, outputSummary, usage, metadata);
    }
}
