package com.huatai.careeragent.agent.core;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentContext(
        @NotNull Long userId,
        @NotNull Long taskId,
        @NotBlank String traceId
) {
}
