package com.huatai.careeragent.agent.context;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record AssembledContext(
        List<String> promptPayloads,
        Set<String> allowedCitationIds,
        Map<String, Object> sourceMetrics
) {
    public AssembledContext {
        promptPayloads = promptPayloads == null ? List.of() : List.copyOf(promptPayloads);
        allowedCitationIds = allowedCitationIds == null ? Set.of() : Set.copyOf(allowedCitationIds);
        sourceMetrics = sourceMetrics == null ? Map.of() : Map.copyOf(sourceMetrics);
    }
}
