package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LangGraphWorkflowReplannerTest {
    @Test
    void sendsVerifierFeedbackAndValidatesReturnedPlan() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("/v1/workflows/career/replan"))
                .andExpect(jsonPath("$.taskId").value(42))
                .andExpect(jsonPath("$.traceId").value("trace-42"))
                .andExpect(jsonPath("$.failedStep").value("ANALYZING_RESUME"))
                .andExpect(jsonPath("$.verification.nextAction").value("REPLAN"))
                .andRespond(withSuccess("""
                        {"runId":"trace-42:replan:1","plannedStatuses":["MATCHING_JOB","ANALYZING_RESUME",
                        "GENERATING_FINAL_REPORT","SUCCESS"]}
                        """, MediaType.APPLICATION_JSON));

        WorkflowPlan plan = fixture.replanner.replan(runtime(), replanVerification());

        assertThat(plan.planId()).isEqualTo("trace-42:replan:1");
        assertThat(plan.steps()).extracting(WorkflowPlanStep::status)
                .containsExactly(WorkflowStatus.MATCHING_JOB, WorkflowStatus.ANALYZING_RESUME,
                        WorkflowStatus.GENERATING_FINAL_REPORT);
        fixture.server.verify();
    }

    @Test
    void rejectsUnsafeReturnedPlan() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("/v1/workflows/career/replan"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"runId":"trace-42:replan:bad","plannedStatuses":["MATCHING_JOB","SUCCESS"]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.replanner.replan(runtime(), replanVerification()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LangGraph plan skipped a required career workflow step");
        fixture.server.verify();
    }

    private WorkflowRuntime runtime() {
        WorkflowPlan plan = new WorkflowPlan("spring-test", List.of(
                new WorkflowPlanStep(WorkflowStatus.MATCHING_JOB, AgentNames.JOB_MATCH_AGENT, true, 2),
                new WorkflowPlanStep(WorkflowStatus.ANALYZING_RESUME, AgentNames.RESUME_ANALYSIS_AGENT, true, 2),
                new WorkflowPlanStep(WorkflowStatus.GENERATING_FINAL_REPORT, AgentNames.FINAL_REPORT_AGENT, true, 2)
        ));
        WorkflowRuntime runtime = new WorkflowRuntime(42L, plan);
        runtime.advance();
        runtime.recordAttempt(new WorkflowStepResult(WorkflowStatus.ANALYZING_RESUME,
                        AgentNames.RESUME_ANALYSIS_AGENT, "RESUME_ANALYSIS_REPORT", null, Map.of()),
                replanVerification());
        return runtime;
    }

    private VerificationResult replanVerification() {
        return VerificationResult.failed(NextAction.REPLAN, "artifact missing",
                Map.of("summaryPresent", false));
    }

    private Fixture fixture() {
        AgentTaskRepository repository = mock(AgentTaskRepository.class);
        AgentTask task = new AgentTask(7L, "trace-42", 10L, 20L,
                List.of(WorkflowStatus.MATCHING_JOB, WorkflowStatus.ANALYZING_RESUME));
        when(repository.findById(42L)).thenReturn(Optional.of(task));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new LangGraphWorkflowReplanner(repository, new WorkflowPlanPolicy(), builder.build()),
                server);
    }

    private record Fixture(LangGraphWorkflowReplanner replanner, MockRestServiceServer server) {
    }
}
