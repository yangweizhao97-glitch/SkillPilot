package com.huatai.careeragent.llm;

import org.springframework.stereotype.Component;

@Component
public class LlmRetrySleeper {
    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmErrorCategory.UNKNOWN, "LLM retry interrupted", false, null, exception);
        }
    }
}
