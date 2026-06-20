package com.huatai.careeragent.agent.workflow;

public interface AgentWorkflowExecutor {
    void execute(Long taskId);

    String engine();
}
