package com.huatai.careeragent.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {
    @Test
    void registersAndGetsTool() {
        ToolRegistry registry = new ToolRegistry(List.of());
        TestTool tool = new TestTool("example");

        registry.register(tool);

        assertThat(registry.getRequired("example")).isSameAs(tool);
        assertThat(registry.names()).containsExactly("example");
    }

    @Test
    void rejectsDuplicateAndUnknownTools() {
        ToolRegistry registry = new ToolRegistry(List.of(new TestTool("example")));

        assertThatThrownBy(() -> registry.register(new TestTool("example")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
        assertThatThrownBy(() -> registry.getRequired("missing"))
                .isInstanceOf(ToolException.class)
                .extracting(exception -> ((ToolException) exception).getCode())
                .isEqualTo("TOOL_NOT_REGISTERED");
    }

    private record TestTool(String name) implements Tool<String, String> {
        @Override
        public Class<String> inputType() {
            return String.class;
        }

        @Override
        public Set<String> allowedAgents() {
            return Set.of("TEST_AGENT");
        }

        @Override
        public String execute(String input, ToolExecutionContext context) {
            return input;
        }
    }
}
