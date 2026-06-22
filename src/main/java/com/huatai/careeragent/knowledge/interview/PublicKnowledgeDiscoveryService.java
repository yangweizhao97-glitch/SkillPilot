package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.agent.tool.ToolException;
import com.huatai.careeragent.mcp.McpClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class PublicKnowledgeDiscoveryService {
    private final McpClient mcpClient;
    private final PublicKnowledgeSearchProperties properties;
    private final PublicKnowledgeSanitizer sanitizer;

    public PublicKnowledgeDiscoveryService(McpClient mcpClient, PublicKnowledgeSearchProperties properties,
                                           PublicKnowledgeSanitizer sanitizer) {
        this.mcpClient = mcpClient;
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    public DiscoveryResponse discover(DiscoveryRequest request) {
        requireConfigured();
        int limit = request.limit() == null ? properties.getMaxResults()
                : Math.min(Math.max(request.limit(), 1), properties.getMaxResults());
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", request.query());
        arguments.put("domains", properties.getAllowedDomains());
        arguments.put("limit", limit);
        var result = mcpClient.callTool(properties.getToolName(), Map.copyOf(arguments));
        Object raw = result.structuredContent().get("results");
        if (!(raw instanceof List<?> values)) {
            throw new ToolException("PUBLIC_SEARCH_INVALID_RESPONSE",
                    "搜索 MCP 必须返回 structuredContent.results", false);
        }
        List<DiscoveryCandidate> candidates = values.stream().filter(Map.class::isInstance)
                .map(Map.class::cast).map(this::candidate).filter(Objects::nonNull).limit(limit).toList();
        return new DiscoveryResponse(candidates);
    }

    public FetchedPage fetch(DiscoveryCandidate candidate) {
        requireConfigured();
        if (!allowed(candidate.url())) {
            throw new ToolException("PUBLIC_SEARCH_DOMAIN_NOT_ALLOWED", "候选页面不在允许域名中", false);
        }
        var result = mcpClient.callTool(properties.getFetchToolName(), Map.of("url", candidate.url()));
        Object raw = result.structuredContent().get("text");
        if (!(raw instanceof String text) || text.isBlank()) {
            throw new ToolException("PUBLIC_FETCH_INVALID_RESPONSE",
                    "页面抓取 MCP 必须返回 structuredContent.text", false);
        }
        String limited = text.length() <= properties.getMaxContentChars()
                ? text : text.substring(0, properties.getMaxContentChars());
        String cleaned = sanitizer.sanitize(limited, "public_knowledge_fetch");
        if (cleaned == null || cleaned.isBlank()) {
            throw new ToolException("PUBLIC_FETCH_EMPTY", "页面清理后没有可用内容", false);
        }
        return new FetchedPage(candidate.title(), candidate.url(), cleaned, candidate.publishedAt());
    }

    private void requireConfigured() {
        if (!properties.isEnabled()) {
            throw new ToolException("PUBLIC_SEARCH_DISABLED", "公共知识搜索未启用", false);
        }
        if (properties.getAllowedDomains().isEmpty()) {
            throw new ToolException("PUBLIC_SEARCH_DOMAINS_REQUIRED", "公共知识搜索必须配置允许域名", false);
        }
    }

    private DiscoveryCandidate candidate(Map<?, ?> raw) {
        String url = text(raw.get("url"));
        if (!allowed(url)) return null;
        String trace = "public_knowledge_discovery";
        String title = sanitizer.sanitize(text(raw.get("title")), trace);
        String snippet = sanitizer.sanitize(text(raw.get("snippet")), trace);
        if (title == null || title.isBlank() || snippet == null || snippet.isBlank()) return null;
        return new DiscoveryCandidate(title, url, snippet, instant(text(raw.get("publishedAt"))));
    }

    private boolean allowed(String value) {
        try {
            URI uri = URI.create(value);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                return false;
            String host = uri.getHost().toLowerCase();
            return properties.getAllowedDomains().stream()
                    .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        } catch (RuntimeException exception) { return false; }
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); } catch (DateTimeParseException ignored) { return null; }
    }

    public record DiscoveryRequest(@jakarta.validation.constraints.NotBlank String query,
                                   @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(20)
                                   Integer limit) { }
    public record DiscoveryResponse(List<DiscoveryCandidate> candidates) { }
    public record DiscoveryCandidate(String title, String url, String snippet, Instant publishedAt) { }
    public record FetchedPage(String title, String url, String text, Instant publishedAt) { }
}
