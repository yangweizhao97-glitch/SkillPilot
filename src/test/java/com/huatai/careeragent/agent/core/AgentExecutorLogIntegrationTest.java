package com.huatai.careeragent.agent.core;

import com.huatai.careeragent.llm.LlmResponse.TokenUsage;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.log.AgentExecutionLog;
import com.huatai.careeragent.task.log.AgentExecutionLogRepository;
import com.huatai.careeragent.task.log.ExecutionLogStatus;
import com.huatai.careeragent.user.User;
import com.huatai.careeragent.user.UserRepository;
import com.huatai.careeragent.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "career-agent.agent.initial-retry-delay=0ms")
class AgentExecutorLogIntegrationTest {
    @Autowired
    private AgentExecutor agentExecutor;

    @Autowired
    private AgentExecutionLogRepository logRepository;

    @Autowired
    private AgentTaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        logRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void persistsAgentExecutionMetricsWithSharedTraceId() {
        User user = userRepository.save(new User(
                "agent-log-" + UUID.randomUUID() + "@example.com", "hash", "Agent Log", UserRole.USER
        ));
        String traceId = "trace_" + UUID.randomUUID().toString().replace("-", "");
        AgentTask task = taskRepository.save(new AgentTask(user.getId(), traceId, null, null, List.of()));
        AgentContext context = new AgentContext(user.getId(), task.getId(), traceId);

        agentExecutor.execute(new Agent<String, String>() {
            @Override
            public String name() { return "LOG_TEST_AGENT"; }

            @Override
            public String stepName() { return "LOG_TEST_STEP"; }

            @Override
            public AgentResult<String> execute(String input, AgentContext agentContext) {
                return AgentResult.success("done", "test output", new TokenUsage(12, 8, 20));
            }
        }, "input", context);

        List<AgentExecutionLog> logs = logRepository.findByTaskIdAndUserIdOrderByCreatedAtAscIdAsc(
                task.getId(), user.getId()
        );
        assertThat(logs).hasSize(1);
        AgentExecutionLog log = logs.getFirst();
        assertThat(log.getTraceId()).isEqualTo(traceId);
        assertThat(log.getAgentName()).isEqualTo("LOG_TEST_AGENT");
        assertThat(log.getStepName()).isEqualTo("LOG_TEST_STEP");
        assertThat(log.getStatus()).isEqualTo(ExecutionLogStatus.SUCCESS);
        assertThat(log.getPromptTokens()).isEqualTo(12);
        assertThat(log.getCompletionTokens()).isEqualTo(8);
        assertThat(log.getTotalTokens()).isEqualTo(20);
        assertThat(log.getDurationMs()).isNotNegative();
    }
}
