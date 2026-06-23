package com.huatai.careeragent.agent.tool;

import java.util.Set;

public record ToolCallingPolicy(
        String agentName,
        Set<String> allowedTools,
        int maxCalls
) {
    public ToolCallingPolicy {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName is required");
        }
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        if (maxCalls < 0) {
            maxCalls = 0;
        }
    }
}
