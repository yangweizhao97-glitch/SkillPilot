package com.huatai.careeragent.agent.handoff;

import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentException;
import com.huatai.careeragent.agent.core.AgentExecutionLogService;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.llm.LlmResponse.TokenUsage;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import com.huatai.careeragent.task.log.ExecutionLogStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class HandoffCoordinator {
    private static final Map<WorkflowStatus, String> AGENTS = Map.of(
            WorkflowStatus.MATCHING_JOB, AgentNames.JOB_MATCH_AGENT,
            WorkflowStatus.ANALYZING_RESUME, AgentNames.RESUME_ANALYSIS_AGENT,
            WorkflowStatus.GENERATING_QUESTIONS, AgentNames.INTERVIEW_QUESTION_AGENT,
            WorkflowStatus.GENERATING_FINAL_REPORT, AgentNames.FINAL_REPORT_AGENT
    );

    private final AgentTaskRepository taskRepository;
    private final HandoffPolicy policy;
    private final HandoffProperties properties;
    private final AgentExecutionLogService logService;

    public HandoffCoordinator(AgentTaskRepository taskRepository, HandoffPolicy policy,
                              HandoffProperties properties, AgentExecutionLogService logService) {
        this.taskRepository = taskRepository;
        this.policy = policy;
        this.properties = properties;
        this.logService = logService;
    }

    public List<WorkflowStatus> activeAgentSteps(Long taskId, List<WorkflowStatus> executionOrder) {
        AgentTask task = requiredTask(taskId);
        List<WorkflowStatus> active = new ArrayList<>();
        for (WorkflowStatus status : executionOrder) {
            if (AGENTS.containsKey(status)
                    && (status == WorkflowStatus.GENERATING_FINAL_REPORT
                        ? task.getJobId() != null : task.getEnabledSteps().contains(status))) {
                active.add(status);
            }
        }
        return List.copyOf(active);
    }

    public void handoff(Long taskId, WorkflowStatus source, WorkflowStatus target,
                        int depth, Set<String> visitedAgents) {
        if (!properties.isEnabled()) {
            return;
        }
        AgentTask task = requiredTask(taskId);
        AgentContext context = new AgentContext(task.getUserId(), task.getId(), task.getTraceId());
        HandoffRequest request = new HandoffRequest(
                source, target, AGENTS.get(source), AGENTS.get(target),
                "Continue task-scoped career analysis", depth, visitedAgents
        );
        String route = request.sourceAgent() + " -> " + request.targetAgent();
        long started = System.nanoTime();
        logService.record(context, request.sourceAgent(), target.name(),
                "handoffDepth=" + depth, "Handoff started: " + route,
                ExecutionLogStatus.HANDOFF_STARTED, 0, TokenUsage.empty(), null);
        try {
            policy.check(request);
            logService.record(context, request.sourceAgent(), target.name(),
                    "handoffDepth=" + depth, "Handoff completed: " + route,
                    ExecutionLogStatus.HANDOFF_COMPLETED, elapsedMs(started), TokenUsage.empty(), null);
        } catch (AgentException exception) {
            logService.record(context, request.sourceAgent() == null ? "UNKNOWN_AGENT" : request.sourceAgent(),
                    target == null ? source.name() : target.name(), "handoffDepth=" + depth,
                    "Handoff rejected", ExecutionLogStatus.HANDOFF_REJECTED, elapsedMs(started),
                    TokenUsage.empty(), exception.getCode());
            throw exception;
        }
    }

    public String agentFor(WorkflowStatus status) {
        return AGENTS.get(status);
    }

    private AgentTask requiredTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new AgentException("HANDOFF_TASK_NOT_FOUND", "Career task not found", false));
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
