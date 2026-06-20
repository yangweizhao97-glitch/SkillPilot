package com.huatai.careeragent.agent.tool;

public class ToolException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public ToolException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
