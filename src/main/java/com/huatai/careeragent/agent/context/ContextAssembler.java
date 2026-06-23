package com.huatai.careeragent.agent.context;

import com.huatai.careeragent.agent.core.AgentContext;

public interface ContextAssembler<I> {
    AssembledContext assemble(I input, AgentContext context, ContextPolicy policy);
}
