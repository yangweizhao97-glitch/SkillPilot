package com.huatai.careeragent.llm;

public record LlmResponse(
        String content,
        String provider,
        String model,
        String finishReason,
        TokenUsage usage,
        long durationMs,
        String requestId
) {
    public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
        public static TokenUsage empty() {
            return new TokenUsage(0, 0, 0);
        }

        public TokenUsage plus(TokenUsage other) {
            if (other == null) {
                return this;
            }
            return new TokenUsage(
                    promptTokens + other.promptTokens,
                    completionTokens + other.completionTokens,
                    totalTokens + other.totalTokens
            );
        }
    }
}
