package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.mcp.McpClient;
import com.huatai.careeragent.mcp.McpProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CallMcpTool implements Tool<CallMcpTool.Input, CallMcpTool.Output> {
    public static final String NAME = "CallMcpTool";

    private final McpClient mcpClient;
    private final McpProperties properties;

    public CallMcpTool(McpClient mcpClient, McpProperties properties) {
        this.mcpClient = mcpClient;
        this.properties = properties;
    }

    @Override public String name() { return NAME; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public Set<String> allowedAgents() { return properties.getAllowedAgents(); }

    @Override
    public Output execute(Input input, ToolExecutionContext context) {
        var result = mcpClient.callTool(input.toolName(), input.arguments());
        return new Output(properties.getServerName(), input.toolName(), result.content(), result.structuredContent());
    }

    public record Input(
            @NotBlank String toolName,
            @NotNull @Size(max = 32) Map<String, Object> arguments
    ) { }

    public record Output(
            String serverName,
            String toolName,
            List<Map<String, Object>> mcpContent,
            Map<String, Object> structuredContent
    ) { }
}
