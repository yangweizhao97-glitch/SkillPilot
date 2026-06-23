package com.huatai.careeragent.agent.tool;

import java.util.Map;

public record PlannedToolCall(
        String toolName,
        Map<String, Object> arguments
) {
    public PlannedToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
