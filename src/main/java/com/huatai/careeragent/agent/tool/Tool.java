package com.huatai.careeragent.agent.tool;

import java.util.Set;

public interface Tool<I, O> {
    String name();

    Class<I> inputType();

    Set<String> allowedAgents();

    O execute(I input, ToolExecutionContext context);
}
