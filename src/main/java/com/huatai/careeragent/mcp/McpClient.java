package com.huatai.careeragent.mcp;

import java.util.List;
import java.util.Map;

public interface McpClient {
    List<McpToolDefinition> listTools();

    McpToolResult callTool(String toolName, Map<String, Object> arguments);
}
