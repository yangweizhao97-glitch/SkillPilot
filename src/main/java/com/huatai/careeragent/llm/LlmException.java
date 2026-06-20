package com.huatai.careeragent.llm;

public class LlmException extends RuntimeException {
    private final LlmErrorCategory category;
    private final boolean retryable;
    private final Integer statusCode;

    public LlmException(LlmErrorCategory category, String message, boolean retryable, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.retryable = retryable;
        this.statusCode = statusCode;
    }

    public LlmErrorCategory getCategory() { return category; }
    public boolean isRetryable() { return retryable; }
    public Integer getStatusCode() { return statusCode; }
}
