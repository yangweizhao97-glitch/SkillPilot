package com.huatai.careeragent.agent.context;

public record ContextPolicy(
        int privateTopK,
        int publicTopK,
        int maxContextItems
) {
    public ContextPolicy {
        if (privateTopK < 1) privateTopK = 1;
        if (publicTopK < 1) publicTopK = 1;
        if (maxContextItems < 1) maxContextItems = 1;
    }

    public static ContextPolicy interviewQuestionsDefault() {
        return new ContextPolicy(8, 8, 24);
    }
}
