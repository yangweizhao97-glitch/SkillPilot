package com.huatai.careeragent.agent.core;

import com.huatai.careeragent.llm.LlmResponse.TokenUsage;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AgentExecutorTest {
    private final AgentExecutionLogService logService = mock(AgentExecutionLogService.class);
    private final AgentRetrySleeper retrySleeper = mock(AgentRetrySleeper.class);
    private final AgentProperties properties = properties();
    private final AgentExecutor executor = new AgentExecutor(
            properties,
            logService,
            retrySleeper,
            Validation.buildDefaultValidatorFactory().getValidator()
    );
    private final AgentContext context = new AgentContext(1L, 2L, "trace-1");

    @Test
    void executesSuccessfullyAndRecordsUsage() {
        Agent<String, String> agent = agent(input -> AgentResult.success(
                "result", "generated result", new TokenUsage(10, 5, 15)
        ));

        AgentResult<String> result = executor.execute(agent, "input", context);

        assertThat(result.output()).isEqualTo("result");
        verify(logService).record(
                any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(com.huatai.careeragent.task.log.ExecutionLogStatus.SUCCESS),
                any(Long.class), org.mockito.ArgumentMatchers.eq(new TokenUsage(10, 5, 15)), any()
        );
    }

    @Test
    void retriesRetryableFailureTwiceThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        Agent<String, String> agent = agent(input -> {
            if (attempts.getAndIncrement() < 2) {
                throw new AgentException("TEMPORARY", "temporary failure", true);
            }
            return AgentResult.success("ok", "ok", TokenUsage.empty());
        });

        assertThat(executor.execute(agent, "input", context).output()).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
        verify(retrySleeper, times(2)).sleep(any(Duration.class));
        verify(logService, times(3)).record(any(), any(), any(), any(), any(), any(), any(Long.class), any(), any());
    }

    @Test
    void doesNotRetryNonRetryableFailure() {
        Agent<String, String> agent = agent(input -> {
            throw new AgentException("DENIED", "denied", false);
        });

        assertThatThrownBy(() -> executor.execute(agent, "input", context))
                .isInstanceOf(AgentException.class)
                .extracting(exception -> ((AgentException) exception).getCode())
                .isEqualTo("DENIED");
        verify(retrySleeper, never()).sleep(any());
        verify(logService, times(1)).record(any(), any(), any(), any(), any(), any(), any(Long.class), any(), any());
    }

    private Agent<String, String> agent(AgentAction action) {
        return new Agent<>() {
            @Override
            public String name() { return "TEST_AGENT"; }

            @Override
            public String stepName() { return "TEST_STEP"; }

            @Override
            public AgentResult<String> execute(String input, AgentContext context) { return action.execute(input); }
        };
    }

    private AgentProperties properties() {
        AgentProperties value = new AgentProperties();
        value.setMaxRetries(2);
        value.setInitialRetryDelay(Duration.ofMillis(1));
        return value;
    }

    @FunctionalInterface
    private interface AgentAction {
        AgentResult<String> execute(String input);
    }
}
