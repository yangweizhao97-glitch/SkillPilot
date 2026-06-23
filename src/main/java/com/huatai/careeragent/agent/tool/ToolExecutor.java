package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.common.error.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ToolExecutor {
    private final ToolRegistry registry;
    private final ToolPermissionChecker permissionChecker;
    private final ToolCallLogService logService;
    private final AuditDataSanitizer sanitizer;
    private final Validator validator;

    public ToolExecutor(
            ToolRegistry registry,
            ToolPermissionChecker permissionChecker,
            ToolCallLogService logService,
            AuditDataSanitizer sanitizer,
            Validator validator
    ) {
        this.registry = registry;
        this.permissionChecker = permissionChecker;
        this.logService = logService;
        this.sanitizer = sanitizer;
        this.validator = validator;
    }

    public ToolResponse<?> execute(ToolRequest<?> request) {
        long start = System.nanoTime();
        Map<String, Object> input = request == null ? null : sanitizer.sanitize(request.input());
        String toolCallId = canLog(request) ? logService.start(request, input) : null;
        try {
            validate(request);
            Tool<?, ?> tool = registry.getRequired(request.toolName());
            if (!tool.inputType().isInstance(request.input())) {
                throw new ToolException("TOOL_INPUT_TYPE_MISMATCH", "Tool input type is invalid", false);
            }
            permissionChecker.check(tool, request.context());
            Object output = invoke(tool, request.input(), request.context());
            logService.complete(
                    toolCallId, sanitizer.sanitize(output), ToolCallStatus.TOOL_COMPLETED,
                    elapsedMs(start),
                    null
            );
            return ToolResponse.success(output);
        } catch (ToolException exception) {
            recordFailure(toolCallId, start, exception.getMessage());
            return ToolResponse.failure(exception.getCode(), exception.getMessage(), exception.isRetryable());
        } catch (BusinessException exception) {
            recordFailure(toolCallId, start, exception.getMessage());
            return ToolResponse.failure(exception.getCode(), exception.getMessage(), false);
        } catch (RuntimeException exception) {
            recordFailure(toolCallId, start, exception.getMessage());
            return ToolResponse.failure("TOOL_EXECUTION_FAILED", "Tool execution failed", true);
        }
    }

    private void validate(ToolRequest<?> request) {
        if (request == null) {
            throw new ToolException("INVALID_TOOL_REQUEST", "Tool request is required", false);
        }
        Set<ConstraintViolation<ToolRequest<?>>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new ToolException("INVALID_TOOL_REQUEST", message, false);
        }
    }

    @SuppressWarnings("unchecked")
    private <I, O> O invoke(Tool<?, ?> rawTool, Object input, ToolExecutionContext context) {
        Tool<I, O> tool = (Tool<I, O>) rawTool;
        return tool.execute((I) input, context);
    }

    private void recordFailure(String toolCallId, long start, String message) {
        if (toolCallId != null) {
            logService.complete(toolCallId, null, ToolCallStatus.TOOL_FAILED, elapsedMs(start), message);
        }
    }

    private boolean canLog(ToolRequest<?> request) {
        return request != null && request.context() != null
                && request.context().userId() != null && request.context().scopeId() != null
                && request.context().traceId() != null && request.context().agentName() != null;
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
