package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LangGraphAgentWorkflowExecutorTest {
    @Test
    void acceptsContractPlanAndPreservesTaskAndTraceContext() {
        Fixture fixture = fixture(false);
        fixture.server.expect(requestTo("/v1/workflows/career/plan"))
                .andExpect(content().json("""
                        {"taskId":42,"traceId":"trace-42","enabledSteps":["MATCHING_JOB","ANALYZING_RESUME"]}
                        """))
                .andRespond(withSuccess(validPlan(), MediaType.APPLICATION_JSON));

        fixture.executor.execute(42L);

        verify(fixture.runner).run(42L, CareerWorkflowRunner.EXECUTION_ORDER);
        fixture.server.verify();
    }

    @Test
    void rejectsInvalidRemotePlanWhenFallbackIsDisabled() {
        Fixture fixture = fixture(false);
        fixture.server.expect(requestTo("/v1/workflows/career/plan"))
                .andRespond(withSuccess("""
                        {"runId":"run-bad","plannedStatuses":["MATCHING_JOB","SUCCESS"]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.executor.execute(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LangGraph orchestration failed");
    }

    @Test
    void usesSpringPlanWhenRemotePlanIsInvalidAndFallbackIsEnabled() {
        Fixture fixture = fixture(true);
        fixture.server.expect(requestTo("/v1/workflows/career/plan"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        fixture.executor.execute(42L);

        verify(fixture.runner).run(42L, CareerWorkflowRunner.EXECUTION_ORDER);
    }

    @Test
    void doesNotRetryBusinessExecutionThroughFallback() {
        Fixture fixture = fixture(true);
        fixture.server.expect(requestTo("/v1/workflows/career/plan"))
                .andRespond(withSuccess(validPlan(), MediaType.APPLICATION_JSON));
        org.mockito.Mockito.doThrow(new IllegalStateException("agent failed"))
                .when(fixture.runner).run(42L, CareerWorkflowRunner.EXECUTION_ORDER);

        assertThatThrownBy(() -> fixture.executor.execute(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("agent failed");
        verify(fixture.runner).run(42L, CareerWorkflowRunner.EXECUTION_ORDER);
    }

    private Fixture fixture(boolean fallback) {
        AgentTaskRepository repository = mock(AgentTaskRepository.class);
        CareerWorkflowRunner runner = mock(CareerWorkflowRunner.class);
        AgentTask task = new AgentTask(7L, "trace-42", 10L, 20L,
                List.of(WorkflowStatus.MATCHING_JOB, WorkflowStatus.ANALYZING_RESUME));
        when(repository.findById(42L)).thenReturn(Optional.of(task));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new LangGraphAgentWorkflowExecutor(repository, runner, builder.build(), fallback),
                runner, server);
    }

    private String validPlan() {
        return """
                {"runId":"run-42","plannedStatuses":["MATCHING_JOB","ANALYZING_RESUME",
                "GENERATING_QUESTIONS","GENERATING_FINAL_REPORT","SUCCESS"]}
                """;
    }

    private record Fixture(LangGraphAgentWorkflowExecutor executor, CareerWorkflowRunner runner,
                           MockRestServiceServer server) { }
}
