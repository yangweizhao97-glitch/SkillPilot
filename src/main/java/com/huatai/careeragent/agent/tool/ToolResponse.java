package com.huatai.careeragent.agent.tool;

public record ToolResponse<O>(boolean success, O output, ToolError error) {
    public static <O> ToolResponse<O> success(O output) {
        return new ToolResponse<>(true, output, null);
    }

    public static <O> ToolResponse<O> failure(String code, String message, boolean retryable) {
        return new ToolResponse<>(false, null, new ToolError(code, message, retryable));
    }

    public record ToolError(String code, String message, boolean retryable) {
    }
}
