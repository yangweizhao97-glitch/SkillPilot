package com.huatai.careeragent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.huatai.careeragent.llm.LlmResponse.TokenUsage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DashScopeLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(DashScopeLlmClient.class);

    private final ModelConfig config;
    private final Validator validator;
    private final LlmRetrySleeper retrySleeper;
    private final RestClient restClient;

    @Autowired
    public DashScopeLlmClient(ModelConfig config, Validator validator, LlmRetrySleeper retrySleeper) {
        this(config, validator, retrySleeper, createRestClient(config));
    }

    DashScopeLlmClient(ModelConfig config, Validator validator, LlmRetrySleeper retrySleeper, RestClient restClient) {
        this.config = config;
        this.validator = validator;
        this.retrySleeper = retrySleeper;
        this.restClient = restClient;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        validateConfiguration();
        validateRequest(request);
        int attempt = 0;
        while (true) {
            long start = System.nanoTime();
            String model = StringUtils.hasText(request.model()) ? request.model() : config.getChatModel();
            try {
                JsonNode body = restClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + config.getDashscopeApiKey())
                        .body(buildPayload(request, model))
                        .retrieve()
                        .body(JsonNode.class);
                LlmResponse response = parseResponse(body, model, elapsedMs(start));
                log.info(
                        "LLM call completed: provider={}, model={}, durationMs={}, promptTokens={}, completionTokens={}, totalTokens={}, traceId={}",
                        response.provider(), response.model(), response.durationMs(), response.usage().promptTokens(),
                        response.usage().completionTokens(), response.usage().totalTokens(), request.traceId()
                );
                return response;
            } catch (RuntimeException exception) {
                LlmException mapped = mapException(exception);
                log.warn(
                        "LLM call failed: provider={}, model={}, durationMs={}, category={}, retryable={}, attempt={}, traceId={}",
                        config.getProvider(), model, elapsedMs(start), mapped.getCategory(), mapped.isRetryable(), attempt,
                        request.traceId()
                );
                if (!mapped.isRetryable() || attempt >= config.getMaxRetries()) {
                    throw mapped;
                }
                retrySleeper.sleep(100L * (1L << Math.min(attempt, 10)));
                attempt++;
            }
        }
    }

    Map<String, Object> buildPayload(LlmRequest request, String model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", request.messages().stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());
        if (request.temperature() != null) {
            payload.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            payload.put("max_tokens", request.maxTokens());
        }
        if (request.jsonMode()) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        return payload;
    }

    LlmResponse parseResponse(JsonNode body, String requestedModel, long durationMs) {
        JsonNode choice = body == null ? null : body.path("choices").path(0);
        String content = choice == null ? null : choice.path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new LlmException(LlmErrorCategory.INVALID_RESPONSE, "LLM response content is missing", true, null, null);
        }
        JsonNode usage = body.path("usage");
        TokenUsage tokenUsage = new TokenUsage(
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                usage.path("total_tokens").asInt(0)
        );
        return new LlmResponse(
                content,
                config.getProvider(),
                body.path("model").asText(requestedModel),
                choice.path("finish_reason").asText(null),
                tokenUsage,
                durationMs,
                body.path("id").asText(null)
        );
    }

    LlmException mapException(RuntimeException exception) {
        if (exception instanceof LlmException llmException) {
            return llmException;
        }
        if (exception instanceof ResourceAccessException) {
            return new LlmException(LlmErrorCategory.TIMEOUT, "LLM request timed out", true, null, exception);
        }
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == 401 || status == 403) {
                return new LlmException(LlmErrorCategory.AUTHENTICATION, "LLM authentication failed", false, status, exception);
            }
            if (status == 429) {
                return new LlmException(LlmErrorCategory.RATE_LIMIT, "LLM rate limit exceeded", true, status, exception);
            }
            if (status >= 500) {
                return new LlmException(LlmErrorCategory.PROVIDER_UNAVAILABLE, "LLM provider unavailable", true, status, exception);
            }
            return new LlmException(LlmErrorCategory.BAD_REQUEST, "LLM request rejected", false, status, exception);
        }
        return new LlmException(LlmErrorCategory.UNKNOWN, "LLM call failed", false, null, exception);
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(config.getDashscopeApiKey())) {
            throw new LlmException(
                    LlmErrorCategory.CONFIGURATION,
                    "DASHSCOPE_API_KEY is not configured",
                    false,
                    null,
                    null
            );
        }
    }

    private void validateRequest(LlmRequest request) {
        Set<ConstraintViolation<LlmRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new LlmException(LlmErrorCategory.BAD_REQUEST, message, false, null, null);
        }
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static RestClient createRestClient(ModelConfig config) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.getReadTimeout());
        return RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
