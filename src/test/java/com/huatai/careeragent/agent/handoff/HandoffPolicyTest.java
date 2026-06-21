package com.huatai.careeragent.agent.handoff;

import com.huatai.careeragent.agent.core.AgentException;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.task.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandoffPolicyTest {
    private final HandoffProperties properties = new HandoffProperties();
    private final HandoffPolicy policy = new HandoffPolicy(properties);

    @Test
    void acceptsForwardAllowlistedHandoff() {
        HandoffRequest request = request(
                WorkflowStatus.MATCHING_JOB, WorkflowStatus.ANALYZING_RESUME,
                AgentNames.JOB_MATCH_AGENT, AgentNames.RESUME_ANALYSIS_AGENT, 1,
                Set.of(AgentNames.JOB_MATCH_AGENT)
        );

        assertThatCode(() -> policy.check(request)).doesNotThrowAnyException();
    }

    @Test
    void rejectsBackwardUnknownLoopingAndOverDepthRoutes() {
        assertRejected(request(
                WorkflowStatus.ANALYZING_RESUME, WorkflowStatus.MATCHING_JOB,
                AgentNames.RESUME_ANALYSIS_AGENT, AgentNames.JOB_MATCH_AGENT, 1, Set.of()
        ), "HANDOFF_DIRECTION_DENIED");
        assertRejected(request(
                WorkflowStatus.MATCHING_JOB, WorkflowStatus.ANALYZING_RESUME,
                AgentNames.JOB_MATCH_AGENT, "UNTRUSTED_AGENT", 1, Set.of()
        ), "HANDOFF_TARGET_DENIED");
        assertRejected(request(
                WorkflowStatus.MATCHING_JOB, WorkflowStatus.ANALYZING_RESUME,
                AgentNames.JOB_MATCH_AGENT, AgentNames.RESUME_ANALYSIS_AGENT, 1,
                Set.of(AgentNames.RESUME_ANALYSIS_AGENT)
        ), "HANDOFF_LOOP_DETECTED");
        assertRejected(request(
                WorkflowStatus.MATCHING_JOB, WorkflowStatus.ANALYZING_RESUME,
                AgentNames.JOB_MATCH_AGENT, AgentNames.RESUME_ANALYSIS_AGENT, 5, Set.of()
        ), "HANDOFF_DEPTH_EXCEEDED");
    }

    private HandoffRequest request(WorkflowStatus sourceStep, WorkflowStatus targetStep,
                                   String sourceAgent, String targetAgent, int depth, Set<String> visited) {
        return new HandoffRequest(sourceStep, targetStep, sourceAgent, targetAgent,
                "Continue task-scoped analysis", depth, visited);
    }

    private void assertRejected(HandoffRequest request, String code) {
        assertThatThrownBy(() -> policy.check(request))
                .isInstanceOf(AgentException.class)
                .extracting(exception -> ((AgentException) exception).getCode())
                .isEqualTo(code);
    }
}
