package com.huatai.careeragent.agent.tool;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ToolRequest<I>(
        @NotBlank String toolName,
        @NotNull @Valid I input,
        @NotNull @Valid ToolExecutionContext context,
        @Min(0) int retryCount
) {
    public ToolRequest(String toolName, I input, ToolExecutionContext context) {
        this(toolName, input, context, 0);
    }
}
