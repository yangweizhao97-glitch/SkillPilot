package com.huatai.careeragent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.tool.ToolException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class McpHttpClientTest {
    private static final String ENDPOINT = "http://mcp.example.test/mcp";

    @Test
    void initializesDiscoversAllowlistedToolsAndCallsThroughTheSession() {
        McpProperties properties = enabledProperties(List.of("search_notes"));
        RestClient.Builder builder = RestClient.builder().baseUrl(ENDPOINT);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        McpHttpClient client = new McpHttpClient(properties, new ObjectMapper(), builder.build());

        server.expect(once(), requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.method").value("initialize"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{},"serverInfo":{"name":"test","version":"1"}}}
                        """, MediaType.APPLICATION_JSON).header("Mcp-Session-Id", "session-1"));
        server.expect(once(), requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Mcp-Session-Id", "session-1"))
                .andExpect(jsonPath("$.method").value("notifications/initialized"))
                .andRespond(withAccepted());
        server.expect(once(), requestTo(ENDPOINT)).andExpect(header("Mcp-Session-Id", "session-1"))
                .andExpect(jsonPath("$.method").value("tools/list"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":2,"result":{"tools":[
                          {"name":"search_notes","description":"Search","inputSchema":{"type":"object"}},
                          {"name":"dangerous_admin","description":"Admin","inputSchema":{"type":"object"}}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(ENDPOINT)).andExpect(header("Mcp-Session-Id", "session-1"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andExpect(jsonPath("$.params.name").value("search_notes"))
                .andExpect(jsonPath("$.params.arguments.query").value("java"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"found"}],"structuredContent":{"count":1}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listTools()).extracting(McpToolDefinition::name).containsExactly("search_notes");
        McpToolResult result = client.callTool("search_notes", Map.of("query", "java"));
        assertThat(result.content()).containsExactly(Map.of("type", "text", "text", "found"));
        assertThat(result.structuredContent()).containsEntry("count", 1);
        server.verify();
    }

    @Test
    void parsesStreamableHttpSseResponses() {
        McpProperties properties = enabledProperties(List.of("lookup"));
        RestClient.Builder builder = RestClient.builder().baseUrl(ENDPOINT);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        McpHttpClient client = new McpHttpClient(properties, new ObjectMapper(), builder.build());
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(
                "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}\n\n",
                MediaType.TEXT_EVENT_STREAM));
        server.expect(requestTo(ENDPOINT)).andRespond(withAccepted());
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(
                "data: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"lookup\",\"inputSchema\":{}}]}}\n\n",
                MediaType.TEXT_EVENT_STREAM));

        assertThat(client.listTools()).extracting(McpToolDefinition::name).containsExactly("lookup");
        server.verify();
    }

    @Test
    void rejectsCallsOutsideTheLocalAllowlistWithoutContactingServer() {
        McpProperties properties = enabledProperties(List.of("safe_tool"));
        RestClient.Builder builder = RestClient.builder().baseUrl(ENDPOINT);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        McpHttpClient client = new McpHttpClient(properties, new ObjectMapper(), builder.build());

        assertThatThrownBy(() -> client.callTool("admin_tool", Map.of()))
                .isInstanceOf(ToolException.class)
                .extracting(exception -> ((ToolException) exception).getCode())
                .isEqualTo("MCP_TOOL_NOT_ALLOWED");
        server.verify();
    }

    private McpProperties enabledProperties(List<String> tools) {
        McpProperties properties = new McpProperties();
        properties.setEnabled(true);
        properties.setEndpoint(ENDPOINT);
        properties.setAllowedTools(tools);
        return properties;
    }
}
