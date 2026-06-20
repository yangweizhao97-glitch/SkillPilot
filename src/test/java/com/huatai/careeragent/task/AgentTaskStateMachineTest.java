package com.huatai.careeragent.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTaskStateMachineTest {
    @Test
    void followsTheCompleteMvpWorkflow() {
        AgentTask task = new AgentTask(
                1L,
                "trace_test",
                10L,
                20L,
                List.of(WorkflowStatus.MATCHING_JOB, WorkflowStatus.ANALYZING_RESUME)
        );

        task.transitionTo(WorkflowStatus.MATCHING_JOB);
        assertThat(task.getStartedAt()).isNotNull();
        assertThat(task.getProgress()).isEqualTo(10);
        task.transitionTo(WorkflowStatus.ANALYZING_RESUME);
        task.transitionTo(WorkflowStatus.GENERATING_QUESTIONS);
        task.transitionTo(WorkflowStatus.GENERATING_FINAL_REPORT);
        task.transitionTo(WorkflowStatus.SUCCESS);

        assertThat(task.getStatus()).isEqualTo(WorkflowStatus.SUCCESS);
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getFinishedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidTransition() {
        AgentTask task = new AgentTask(1L, "trace_test", 10L, 20L, List.of());

        assertThatThrownBy(() -> task.transitionTo(WorkflowStatus.ANALYZING_RESUME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING -> ANALYZING_RESUME");
    }

    @Test
    void canFailFromIntermediateStateAndKeepsErrorSummary() {
        AgentTask task = new AgentTask(1L, "trace_test", 10L, 20L, List.of());
        task.transitionTo(WorkflowStatus.MATCHING_JOB);

        task.fail("Provider timeout");

        assertThat(task.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getErrorMessage()).isEqualTo("Provider timeout");
        assertThat(task.getFinishedAt()).isNotNull();
    }
}
