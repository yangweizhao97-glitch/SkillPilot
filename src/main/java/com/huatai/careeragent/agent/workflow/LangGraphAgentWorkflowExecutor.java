package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "career-agent.workflow.engine", havingValue = "langgraph")
public class LangGraphAgentWorkflowExecutor implements AgentWorkflowExecutor {
    private static final Logger log = LoggerFactory.getLogger(LangGraphAgentWorkflowExecutor.class);

    private final AgentTaskRepository taskRepository;
    private final CareerWorkflowRunner runner;
    private final RestClient client;
    private final boolean fallbackEnabled;

    public LangGraphAgentWorkflowExecutor(
            AgentTaskRepository taskRepository,
            CareerWorkflowRunner runner,
            @Value("${career-agent.workflow.langgraph.base-url:http://localhost:8090}") String baseUrl,
            @Value("${career-agent.workflow.langgraph.connect-timeout:2s}") Duration connectTimeout,
            @Value("${career-agent.workflow.langgraph.read-timeout:10s}") Duration readTimeout,
            @Value("${career-agent.workflow.langgraph.fallback-enabled:true}") boolean fallbackEnabled
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.taskRepository = taskRepository;
        this.runner = runner;
        this.fallbackEnabled = fallbackEnabled;
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    LangGraphAgentWorkflowExecutor(AgentTaskRepository taskRepository, CareerWorkflowRunner runner,
                                   RestClient client, boolean fallbackEnabled) {
        this.taskRepository = taskRepository;
        this.runner = runner;
        this.client = client;
        this.fallbackEnabled = fallbackEnabled;
    }

    @Override
    public void execute(Long taskId) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        List<WorkflowStatus> plan;
        try {
            WorkflowPlanResponse response = client.post()
                    .uri("/v1/workflows/career/plan")
                    .body(new WorkflowPlanRequest(
                            taskId, task.getTraceId(), task.getEnabledSteps().stream().map(Enum::name).toList()
                    ))
                    .retrieve()
                    .body(WorkflowPlanResponse.class);
            plan = validatePlan(response, task);
            log.info("LangGraph workflow plan accepted: taskId={}, runId={}, steps={}",
                    taskId, response.runId(), plan);
        } catch (RuntimeException exception) {
            if (!fallbackEnabled) {
                throw new IllegalStateException("LangGraph orchestration failed", exception);
            }
            log.warn("LangGraph unavailable or returned an invalid plan; using Spring fallback: taskId={}, reason={}",
                    taskId, exception.getMessage());
            plan = CareerWorkflowRunner.EXECUTION_ORDER;
        }
        runner.run(taskId, plan);
    }

    private List<WorkflowStatus> validatePlan(WorkflowPlanResponse response, AgentTask task) {
        if (response == null || response.plannedStatuses() == null || response.runId() == null
                || response.runId().isBlank()) {
            throw new IllegalArgumentException("LangGraph returned an incomplete workflow plan");
        }
        List<WorkflowStatus> statuses;
        try {
            statuses = response.plannedStatuses().stream().map(WorkflowStatus::valueOf).toList();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("LangGraph returned an unknown workflow status", exception);
        }
        if (!isLegalSubsequence(statuses)) {
            throw new IllegalArgumentException("LangGraph plan violates the career workflow ordering contract");
        }
        List<WorkflowStatus> required = requiredStatuses(task);
        if (!statuses.containsAll(required)) {
            throw new IllegalArgumentException("LangGraph plan skipped a required career workflow step");
        }
        return statuses;
    }

    private boolean isLegalSubsequence(List<WorkflowStatus> statuses) {
        if (statuses.isEmpty() || statuses.getLast() != WorkflowStatus.SUCCESS
                || statuses.stream().distinct().count() != statuses.size()) {
            return false;
        }
        int cursor = -1;
        for (WorkflowStatus status : statuses) {
            int index = CareerWorkflowRunner.EXECUTION_ORDER.indexOf(status);
            if (index < 0 || index <= cursor) {
                return false;
            }
            cursor = index;
        }
        return true;
    }

    private List<WorkflowStatus> requiredStatuses(AgentTask task) {
        java.util.ArrayList<WorkflowStatus> required = new java.util.ArrayList<>();
        required.addAll(task.getEnabledSteps());
        if (task.getJobId() != null) {
            required.add(WorkflowStatus.GENERATING_FINAL_REPORT);
        }
        required.add(WorkflowStatus.SUCCESS);
        return List.copyOf(required);
    }

    @Override
    public String engine() {
        return "langgraph";
    }

    record WorkflowPlanRequest(Long taskId, String traceId, List<String> enabledSteps) { }
    record WorkflowPlanResponse(String runId, List<String> plannedStatuses) { }
}
