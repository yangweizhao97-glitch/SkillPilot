package com.huatai.careeragent.llm;

public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
