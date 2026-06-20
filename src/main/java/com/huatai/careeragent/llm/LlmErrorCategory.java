package com.huatai.careeragent.llm;

public enum LlmErrorCategory {
    CONFIGURATION,
    AUTHENTICATION,
    RATE_LIMIT,
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    INVALID_RESPONSE,
    BAD_REQUEST,
    UNKNOWN
}
