package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.agent.tool.ToolException;
import com.huatai.careeragent.mcp.McpClient;
import com.huatai.careeragent.mcp.McpToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicKnowledgeDiscoveryServiceTest {
    @Test
    void staysDisabledByDefault() {
        PublicKnowledgeDiscoveryService service = new PublicKnowledgeDiscoveryService(mock(McpClient.class),
                new PublicKnowledgeSearchProperties(), new PublicKnowledgeSanitizer());

        assertThatThrownBy(() -> service.discover(new PublicKnowledgeDiscoveryService.DiscoveryRequest("Java", 5)))
                .isInstanceOf(ToolException.class).hasMessageContaining("未启用");
    }

    @Test
    void keepsOnlyAllowlistedTraceableSearchResults() {
        McpClient client = mock(McpClient.class);
        PublicKnowledgeSearchProperties properties = new PublicKnowledgeSearchProperties();
        properties.setEnabled(true);
        properties.setAllowedDomains(List.of("nowcoder.com"));
        when(client.callTool(eq("search_web"), anyMap())).thenReturn(new McpToolResult(List.of(), Map.of(
                "results", List.of(
                        Map.of("title", "Java 面经", "url", "https://www.nowcoder.com/discuss/1",
                                "snippet", "二面讨论了线程池"),
                        Map.of("title", "未知来源", "url", "https://example.org/post",
                                "snippet", "不应进入候选集")
                ))));
        PublicKnowledgeDiscoveryService service = new PublicKnowledgeDiscoveryService(client, properties,
                new PublicKnowledgeSanitizer());

        var response = service.discover(new PublicKnowledgeDiscoveryService.DiscoveryRequest("Java", 5));

        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().url()).contains("nowcoder.com");
    }
}
