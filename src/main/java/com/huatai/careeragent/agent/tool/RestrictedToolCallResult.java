package com.huatai.careeragent.agent.tool;

import java.util.Map;

public record RestrictedToolCallResult(
        String toolName,
        Map<String, Object> output
) {
    public RestrictedToolCallResult {
        output = output == null ? Map.of() : Map.copyOf(output);
    }
}
