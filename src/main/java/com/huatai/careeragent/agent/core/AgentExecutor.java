package com.huatai.careeragent.agent.core;

import com.huatai.careeragent.agent.schema.SchemaOutputException;
import com.huatai.careeragent.llm.LlmException;
import com.huatai.careeragent.llm.LlmResponse.TokenUsage;
import com.huatai.careeragent.task.log.ExecutionLogStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AgentExecutor {
    private final AgentProperties properties;
    private final AgentExecutionLogService logService;
    private final AgentRetrySleeper retrySleeper;
    private final Validator validator;

    public AgentExecutor(
            AgentProperties properties,
            AgentExecutionLogService logService,
            AgentRetrySleeper retrySleeper,
            Validator validator
    ) {
        this.properties = properties;
        this.logService = logService;
        this.retrySleeper = retrySleeper;
        this.validator = validator;
    }

    public <I, O> AgentResult<O> execute(Agent<I, O> agent, I input, AgentContext context) {
        validateContext(context);
        String inputSummary = summarize(agent.summarizeInput(input));
        int attempt = 0;
        while (true) {
            long start = System.nanoTime();
            logService.record(
                    context, agent.name(), agent.stepName(), inputSummary, "Agent started",
                    ExecutionLogStatus.STEP_STARTED, 0, TokenUsage.empty(), null
            );
            try {
                AgentResult<O> result = agent.execute(input, context);
                logService.record(
                        context, agent.name(), agent.stepName(), inputSummary, summarize(result.outputSummary()),
                        ExecutionLogStatus.STEP_COMPLETED, elapsedMs(start), result.usage(), null
                );
                return result;
            } catch (RuntimeException exception) {
                Failure failure = classify(exception);
                logService.record(
                        context, agent.name(), agent.stepName(), inputSummary, "attempt=" + (attempt + 1),
                        ExecutionLogStatus.STEP_FAILED, elapsedMs(start), TokenUsage.empty(), summarize(failure.message())
                );
                if (!failure.retryable() || attempt >= properties.getMaxRetries()) {
                    throw new AgentException(failure.code(), failure.message(), false, exception);
                }
                retrySleeper.sleep(backoff(attempt));
                attempt++;
            }
        }
    }

    private void validateContext(AgentContext context) {
        Set<ConstraintViolation<AgentContext>> violations = validator.validate(context);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new AgentException("INVALID_AGENT_CONTEXT", message, false);
        }
    }

    private Failure classify(RuntimeException exception) {
        if (exception instanceof AgentException agentException) {
            return new Failure(agentException.getCode(), agentException.getMessage(), agentException.isRetryable());
        }
        if (exception instanceof LlmException llmException) {
            return new Failure("LLM_" + llmException.getCategory().name(), llmException.getMessage(), llmException.isRetryable());
        }
        if (exception instanceof SchemaOutputException schemaException) {
            return new Failure(schemaException.getCode(), schemaException.getMessage(), true);
        }
        return new Failure("AGENT_EXECUTION_FAILED", "Agent execution failed", false);
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(attempt, 10);
        return properties.getInitialRetryDelay().multipliedBy(multiplier);
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private String summarize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }

    private record Failure(String code, String message, boolean retryable) {
    }
}
