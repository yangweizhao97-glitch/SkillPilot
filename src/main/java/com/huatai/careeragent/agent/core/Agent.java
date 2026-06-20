package com.huatai.careeragent.agent.core;

public interface Agent<I, O> {
    String name();

    String stepName();

    AgentResult<O> execute(I input, AgentContext context);

    default String summarizeInput(I input) {
        return input == null ? "null" : input.getClass().getSimpleName();
    }
}
