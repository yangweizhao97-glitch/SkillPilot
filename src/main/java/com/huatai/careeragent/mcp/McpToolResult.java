package com.huatai.careeragent.mcp;

import java.util.List;
import java.util.Map;

public record McpToolResult(
        List<Map<String, Object>> content,
        Map<String, Object> structuredContent
) {
}
