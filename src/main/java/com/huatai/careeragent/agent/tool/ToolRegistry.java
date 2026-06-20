package com.huatai.careeragent.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {
    private final Map<String, Tool<?, ?>> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool<?, ?>> discoveredTools) {
        discoveredTools.forEach(this::register);
    }

    public void register(Tool<?, ?> tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        Tool<?, ?> existing = tools.putIfAbsent(tool.name(), tool);
        if (existing != null) {
            throw new IllegalStateException("Tool already registered: " + tool.name());
        }
    }

    public Tool<?, ?> getRequired(String name) {
        Tool<?, ?> tool = tools.get(name);
        if (tool == null) {
            throw new ToolException("TOOL_NOT_REGISTERED", "Tool is not registered: " + name, false);
        }
        return tool;
    }

    public List<String> names() {
        return tools.keySet().stream().sorted().toList();
    }
}
