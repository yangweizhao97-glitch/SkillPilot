package com.huatai.careeragent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.core.AgentContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestrictedToolCallingServiceTest {
    private final ToolRegistry registry = mock(ToolRegistry.class);
    private final ToolExecutor executor = mock(ToolExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestrictedToolCallingService service = new RestrictedToolCallingService(
            registry, executor, new AuditDataSanitizer(objectMapper), objectMapper
    );

    @Test
    void executesWhitelistedCallsWithTypedInputsAndSanitizedOutputs() {
        Tool<TestInput, TestOutput> tool = tool("allowedTool", TestInput.class);
        doReturn(tool).when(registry).getRequired("allowedTool");
        when(executor.execute(any())).thenAnswer(invocation -> {
            ToolRequest<?> request = invocation.getArgument(0);
            assertThat(request.toolName()).isEqualTo("allowedTool");
            assertThat(request.input()).isInstanceOf(TestInput.class);
            assertThat(((TestInput) request.input()).query()).isEqualTo("Java backend");
            assertThat(request.context().agentName()).isEqualTo("TEST_AGENT");
            return ToolResponse.success(new TestOutput("answer", "Bearer abc.def"));
        });

        List<RestrictedToolCallResult> results = service.execute(
                List.of(new PlannedToolCall("allowedTool", Map.of("query", "Java backend"))),
                new ToolCallingPolicy("TEST_AGENT", Set.of("allowedTool"), 1),
                new AgentContext(7L, 42L, "trace-42")
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().output()).containsEntry("value", "answer");
        assertThat(results.getFirst().output()).containsEntry("secret", "***");
        verify(registry).getRequired("allowedTool");
    }

    @Test
    void rejectsCallsOutsideTheWhitelist() {
        assertThatThrownBy(() -> service.execute(
                List.of(new PlannedToolCall("forbiddenTool", Map.of())),
                new ToolCallingPolicy("TEST_AGENT", Set.of("allowedTool"), 1),
                new AgentContext(7L, 42L, "trace-42")
        )).isInstanceOf(ToolException.class)
                .extracting(error -> ((ToolException) error).getCode())
                .isEqualTo("TOOL_NOT_ALLOWED");
    }

    @Test
    void rejectsPlansThatExceedTheCallBudget() {
        assertThatThrownBy(() -> service.execute(
                List.of(
                        new PlannedToolCall("allowedTool", Map.of("query", "one")),
                        new PlannedToolCall("allowedTool", Map.of("query", "two"))
                ),
                new ToolCallingPolicy("TEST_AGENT", Set.of("allowedTool"), 1),
                new AgentContext(7L, 42L, "trace-42")
        )).isInstanceOf(ToolException.class)
                .extracting(error -> ((ToolException) error).getCode())
                .isEqualTo("TOOL_CALL_LIMIT_EXCEEDED");
    }

    @SuppressWarnings("unchecked")
    private Tool<TestInput, TestOutput> tool(String name, Class<TestInput> inputType) {
        Tool<TestInput, TestOutput> tool = mock(Tool.class);
        when(tool.name()).thenReturn(name);
        when(tool.inputType()).thenReturn(inputType);
        when(tool.allowedAgents()).thenReturn(Set.of("TEST_AGENT"));
        return tool;
    }

    private record TestInput(String query) { }
    private record TestOutput(String value, String secret) { }
}
