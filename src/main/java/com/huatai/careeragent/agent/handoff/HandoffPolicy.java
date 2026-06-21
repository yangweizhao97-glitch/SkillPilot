package com.huatai.careeragent.agent.handoff;

import com.huatai.careeragent.agent.core.AgentException;
import com.huatai.careeragent.agent.tool.AgentNames;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class HandoffPolicy {
    private static final Map<String, Set<String>> ALLOWED_TARGETS = Map.of(
            AgentNames.JOB_MATCH_AGENT, Set.of(
                    AgentNames.RESUME_ANALYSIS_AGENT,
                    AgentNames.INTERVIEW_QUESTION_AGENT,
                    AgentNames.FINAL_REPORT_AGENT
            ),
            AgentNames.RESUME_ANALYSIS_AGENT, Set.of(
                    AgentNames.INTERVIEW_QUESTION_AGENT,
                    AgentNames.FINAL_REPORT_AGENT
            ),
            AgentNames.INTERVIEW_QUESTION_AGENT, Set.of(AgentNames.FINAL_REPORT_AGENT)
    );

    private final HandoffProperties properties;

    public HandoffPolicy(HandoffProperties properties) {
        this.properties = properties;
    }

    public void check(HandoffRequest request) {
        if (!properties.isEnabled()) {
            throw rejected("HANDOFF_DISABLED", "Agent handoff is disabled");
        }
        if (request == null || request.sourceStep() == null || request.targetStep() == null
                || request.sourceAgent() == null || request.targetAgent() == null) {
            throw rejected("INVALID_HANDOFF", "Handoff request is incomplete");
        }
        if (request.depth() < 1 || request.depth() > properties.getMaxDepth()) {
            throw rejected("HANDOFF_DEPTH_EXCEEDED", "Handoff depth exceeded the configured limit");
        }
        if (request.sourceStep().isTerminal() || request.targetStep().isTerminal()
                || request.sourceStep().progress() >= request.targetStep().progress()) {
            throw rejected("HANDOFF_DIRECTION_DENIED", "Handoff must move forward in the career workflow");
        }
        if (!ALLOWED_TARGETS.getOrDefault(request.sourceAgent(), Set.of()).contains(request.targetAgent())) {
            throw rejected("HANDOFF_TARGET_DENIED", "Target Agent is not allowed for this source Agent");
        }
        if (request.visitedAgents().contains(request.targetAgent())) {
            throw rejected("HANDOFF_LOOP_DETECTED", "Handoff target was already visited");
        }
    }

    private AgentException rejected(String code, String message) {
        return new AgentException(code, message, false);
    }
}
