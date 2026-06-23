package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.interview.InterviewSessionRepository;
import com.huatai.careeragent.tutor.TutorSessionRepository;
import org.springframework.stereotype.Component;

@Component
public class ToolPermissionChecker {
    private final AgentTaskRepository agentTaskRepository;
    private final TutorSessionRepository tutorSessionRepository;
    private final InterviewSessionRepository interviewSessionRepository;

    public ToolPermissionChecker(AgentTaskRepository agentTaskRepository,
                                 TutorSessionRepository tutorSessionRepository,
                                 InterviewSessionRepository interviewSessionRepository) {
        this.agentTaskRepository = agentTaskRepository;
        this.tutorSessionRepository = tutorSessionRepository;
        this.interviewSessionRepository = interviewSessionRepository;
    }

    public void check(Tool<?, ?> tool, ToolExecutionContext context) {
        if (!tool.allowedAgents().contains(context.agentName())) {
            throw new ToolException(
                    "TOOL_AGENT_NOT_ALLOWED",
                    "Agent is not allowed to call tool: " + tool.name(),
                    false
            );
        }
        switch (context.scopeType()) {
            case TASK -> checkTask(context);
            case TUTOR_SESSION -> checkTutorSession(context);
            case INTERVIEW_SESSION -> checkInterviewSession(context);
        }
    }

    private void checkTask(ToolExecutionContext context) {
        AgentTask task = agentTaskRepository.findByIdAndUserId(context.taskId(), context.userId())
                .orElseThrow(() -> new ToolException(
                        "TOOL_TASK_ACCESS_DENIED",
                        "Task is not available to the current user",
                        false
                ));
        if (!task.getTraceId().equals(context.traceId())) {
            throw new ToolException("TOOL_TRACE_MISMATCH", "Tool traceId does not match task", false);
        }
    }

    private void checkTutorSession(ToolExecutionContext context) {
        tutorSessionRepository.findByIdAndUserId(context.scopeId(), context.userId())
                .orElseThrow(() -> new ToolException(
                        "TOOL_TUTOR_SESSION_ACCESS_DENIED",
                        "Tutor session is not available to the current user",
                        false
                ));
    }

    private void checkInterviewSession(ToolExecutionContext context) {
        interviewSessionRepository.findByIdAndUserId(context.scopeId(), context.userId())
                .orElseThrow(() -> new ToolException(
                        "TOOL_INTERVIEW_SESSION_ACCESS_DENIED",
                        "Interview session is not available to the current user",
                        false
                ));
    }
}
