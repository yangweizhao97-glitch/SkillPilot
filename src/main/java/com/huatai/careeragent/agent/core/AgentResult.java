package com.huatai.careeragent.agent.core;

import com.huatai.careeragent.llm.LlmResponse.TokenUsage;

public record AgentResult<O>(O output, String outputSummary, TokenUsage usage) {
    public AgentResult {
        usage = usage == null ? TokenUsage.empty() : usage;
    }

    public static <O> AgentResult<O> success(O output, String outputSummary, TokenUsage usage) {
        return new AgentResult<>(output, outputSummary, usage);
    }
}
