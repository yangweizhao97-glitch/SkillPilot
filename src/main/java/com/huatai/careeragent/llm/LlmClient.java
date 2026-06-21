package com.huatai.careeragent.llm;

import java.util.function.Consumer;

public interface LlmClient {
    LlmResponse complete(LlmRequest request);

    default LlmResponse stream(LlmRequest request, Consumer<String> onDelta) {
        LlmResponse response = complete(request);
        if (response != null && response.content() != null && !response.content().isEmpty()) {
            onDelta.accept(response.content());
        }
        return response;
    }
}
