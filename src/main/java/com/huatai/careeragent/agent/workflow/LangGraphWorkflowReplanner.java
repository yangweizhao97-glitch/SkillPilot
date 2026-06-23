package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "career-agent.workflow.engine", havingValue = "langgraph")
public class LangGraphWorkflowReplanner implements WorkflowReplanner {
    private final AgentTaskRepository taskRepository;
    private final WorkflowPlanPolicy planPolicy;
    private final RestClient client;

    public LangGraphWorkflowReplanner(
            AgentTaskRepository taskRepository,
            WorkflowPlanPolicy planPolicy,
            @Value("${career-agent.workflow.langgraph.base-url:http://localhost:8090}") String baseUrl,
            @Value("${career-agent.workflow.langgraph.connect-timeout:2s}") Duration connectTimeout,
            @Value("${career-agent.workflow.langgraph.read-timeout:10s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.taskRepository = taskRepository;
        this.planPolicy = planPolicy;
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    LangGraphWorkflowReplanner(AgentTaskRepository taskRepository, WorkflowPlanPolicy planPolicy,
                               RestClient client) {
        this.taskRepository = taskRepository;
        this.planPolicy = planPolicy;
        this.client = client;
    }

    @Override
    public WorkflowPlan replan(WorkflowRuntime runtime, VerificationResult verification) {
        AgentTask task = taskRepository.findById(runtime.taskId())
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + runtime.taskId()));
        WorkflowPlanResponse response = client.post()
                .uri("/v1/workflows/career/replan")
                .body(new WorkflowReplanRequest(
                        runtime.taskId(),
                        task.getTraceId(),
                        task.getEnabledSteps().stream().map(Enum::name).toList(),
                        runtime.plan().steps().stream().map(step -> step.status().name()).toList(),
                        runtime.currentStep().status().name(),
                        runtime.currentStep().agentName(),
                        verification,
                        runtime.snapshotMetrics()
                ))
                .retrieve()
                .body(WorkflowPlanResponse.class);
        if (response == null) {
            throw new IllegalStateException("LangGraph returned an empty replan response");
        }
        List<WorkflowStatus> statuses = planPolicy.validateRemotePlan(response.runId(), response.plannedStatuses(), task);
        return new WorkflowPlan(response.runId(), statuses.stream()
                .filter(status -> status != WorkflowStatus.SUCCESS)
                .map(status -> new WorkflowPlanStep(status, agentName(status), true, 2))
                .toList());
    }

    private String agentName(WorkflowStatus status) {
        return switch (status) {
            case MATCHING_JOB -> AgentNames.JOB_MATCH_AGENT;
            case ANALYZING_RESUME -> AgentNames.RESUME_ANALYSIS_AGENT;
            case GENERATING_QUESTIONS -> AgentNames.INTERVIEW_QUESTION_AGENT;
            case GENERATING_FINAL_REPORT -> AgentNames.FINAL_REPORT_AGENT;
            default -> "CAREER_WORKFLOW";
        };
    }

    record WorkflowReplanRequest(
            Long taskId,
            String traceId,
            List<String> enabledSteps,
            List<String> currentPlan,
            String failedStep,
            String failedAgent,
            VerificationResult verification,
            Map<String, Object> runtime
    ) {
    }

    record WorkflowPlanResponse(String runId, List<String> plannedStatuses) {
    }
}
