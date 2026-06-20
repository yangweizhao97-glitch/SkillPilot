package com.huatai.careeragent.agent.tool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ToolExecutionContext(
        @NotNull Long userId,
        @NotNull Long taskId,
        @NotBlank String traceId,
        @NotBlank String agentName
) {
}
