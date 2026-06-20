package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import org.springframework.stereotype.Component;

@Component
public class ToolPermissionChecker {
    private final AgentTaskRepository agentTaskRepository;

    public ToolPermissionChecker(AgentTaskRepository agentTaskRepository) {
        this.agentTaskRepository = agentTaskRepository;
    }

    public void check(Tool<?, ?> tool, ToolExecutionContext context) {
        AgentTask task = agentTaskRepository.findByIdAndUserId(context.taskId(), context.userId())
                .orElseThrow(() -> new ToolException(
                        "TOOL_TASK_ACCESS_DENIED",
                        "Task is not available to the current user",
                        false
                ));
        if (!task.getTraceId().equals(context.traceId())) {
            throw new ToolException("TOOL_TRACE_MISMATCH", "Tool traceId does not match task", false);
        }
        if (!tool.allowedAgents().contains(context.agentName())) {
            throw new ToolException(
                    "TOOL_AGENT_NOT_ALLOWED",
                    "Agent is not allowed to call tool: " + tool.name(),
                    false
            );
        }
    }
}
