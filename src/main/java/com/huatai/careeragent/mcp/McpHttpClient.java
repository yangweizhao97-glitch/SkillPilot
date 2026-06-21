package com.huatai.careeragent.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huatai.careeragent.agent.tool.ToolException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class McpHttpClient implements McpClient {
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String PROTOCOL_HEADER = "MCP-Protocol-Version";

    private final McpProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final AtomicLong requestIds = new AtomicLong();
    private volatile String sessionId;
    private volatile Map<String, McpToolDefinition> discoveredTools;

    @Autowired
    public McpHttpClient(McpProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, buildRestClient(properties));
    }

    McpHttpClient(McpProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public List<McpToolDefinition> listTools() {
        ensureAvailable();
        ensureInitialized();
        return properties.getAllowedTools().stream()
                .map(discoveredTools::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public McpToolResult callTool(String toolName, Map<String, Object> arguments) {
        ensureAvailable();
        if (toolName == null || !properties.getAllowedTools().contains(toolName)) {
            throw new ToolException("MCP_TOOL_NOT_ALLOWED", "MCP tool is not allowlisted", false);
        }
        ensureInitialized();
        if (!discoveredTools.containsKey(toolName)) {
            throw new ToolException("MCP_TOOL_UNAVAILABLE", "Allowlisted MCP tool was not advertised by the server", false);
        }
        Map<String, Object> safeArguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        enforceRequestLimit(safeArguments);
        JsonNode result = rpc("tools/call", Map.of("name", toolName, "arguments", safeArguments));
        if (result.path("isError").asBoolean(false)) {
            throw new ToolException("MCP_REMOTE_TOOL_FAILED", "MCP server reported a tool failure", false);
        }
        List<Map<String, Object>> content = result.path("content").isArray()
                ? objectMapper.convertValue(result.path("content"), new TypeReference<>() { }) : List.of();
        Map<String, Object> structured = result.path("structuredContent").isObject()
                ? objectMapper.convertValue(result.path("structuredContent"), new TypeReference<>() { }) : Map.of();
        return new McpToolResult(content, structured);
    }

    private synchronized void ensureInitialized() {
        if (discoveredTools != null) {
            return;
        }
        JsonNode initialized = rpc("initialize", Map.of(
                "protocolVersion", properties.getProtocolVersion(),
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "career-agent", "version", "0.2.5")
        ));
        String negotiated = initialized.path("protocolVersion").asText();
        if (!properties.getProtocolVersion().equals(negotiated)) {
            clearSession();
            throw new ToolException("MCP_PROTOCOL_MISMATCH", "MCP server negotiated an unsupported protocol version", false);
        }
        notifyInitialized();
        discoveredTools = discoverTools();
    }

    private Map<String, McpToolDefinition> discoverTools() {
        Map<String, McpToolDefinition> tools = new LinkedHashMap<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            Map<String, Object> params = cursor == null ? Map.of() : Map.of("cursor", cursor);
            JsonNode result = rpc("tools/list", params);
            for (JsonNode tool : result.path("tools")) {
                String name = tool.path("name").asText("");
                if (!name.isBlank() && properties.getAllowedTools().contains(name)) {
                    Map<String, Object> schema = tool.path("inputSchema").isObject()
                            ? objectMapper.convertValue(tool.path("inputSchema"), new TypeReference<>() { }) : Map.of();
                    tools.put(name, new McpToolDefinition(name, tool.path("description").asText(""), schema));
                }
            }
            cursor = result.path("nextCursor").asText(null);
            if (cursor == null || cursor.isBlank()) {
                return Map.copyOf(tools);
            }
        }
        throw new ToolException("MCP_DISCOVERY_LIMIT", "MCP tool discovery exceeded the pagination limit", false);
    }

    private JsonNode rpc(String method, Map<String, Object> params) {
        long requestId = requestIds.incrementAndGet();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.put("id", requestId);
        payload.put("method", method);
        payload.set("params", objectMapper.valueToTree(params));
        String body;
        try {
            var response = restClient.post()
                    .uri("")
                    .headers(this::applyHeaders)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);
            if ("initialize".equals(method)) {
                sessionId = response.getHeaders().getFirst(SESSION_HEADER);
            }
            body = response.getBody();
        } catch (RestClientException exception) {
            throw new ToolException("MCP_SERVER_UNAVAILABLE", "MCP server request failed", true);
        }
        JsonNode envelope = parseEnvelope(body);
        if (envelope.hasNonNull("error")) {
            String code = envelope.path("error").path("code").asText("unknown");
            throw new ToolException("MCP_REMOTE_ERROR", "MCP server returned JSON-RPC error " + code, false);
        }
        if (!"2.0".equals(envelope.path("jsonrpc").asText())
                || envelope.path("id").asLong(Long.MIN_VALUE) != requestId
                || !envelope.has("result")) {
            throw new ToolException("MCP_INVALID_RESPONSE", "MCP server returned an invalid JSON-RPC response", false);
        }
        return envelope.path("result");
    }

    private void notifyInitialized() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.put("method", "notifications/initialized");
        try {
            restClient.post().uri("").headers(this::applyHeaders).body(payload).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            clearSession();
            throw new ToolException("MCP_SERVER_UNAVAILABLE", "MCP initialization notification failed", true);
        }
    }

    private JsonNode parseEnvelope(String body) {
        if (body == null || body.isBlank()) {
            throw new ToolException("MCP_INVALID_RESPONSE", "MCP server returned an empty response", false);
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > properties.getMaxResponseBytes()) {
            throw new ToolException("MCP_RESPONSE_TOO_LARGE", "MCP response exceeded the configured limit", false);
        }
        String json = body.stripLeading().startsWith("data:")
                ? body.lines().map(String::trim).filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim()).filter(line -> !line.isBlank()).reduce((first, second) -> second)
                    .orElse("")
                : body;
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new ToolException("MCP_INVALID_RESPONSE", "MCP server returned malformed JSON", false);
        }
    }

    private void applyHeaders(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.set(PROTOCOL_HEADER, properties.getProtocolVersion());
        if (sessionId != null && !sessionId.isBlank()) {
            headers.set(SESSION_HEADER, sessionId);
        }
        if (!properties.getBearerToken().isBlank()) {
            headers.setBearerAuth(properties.getBearerToken());
        }
    }

    private void enforceRequestLimit(Map<String, Object> arguments) {
        try {
            if (objectMapper.writeValueAsBytes(arguments).length > properties.getMaxRequestBytes()) {
                throw new ToolException("MCP_REQUEST_TOO_LARGE", "MCP tool arguments exceeded the configured limit", false);
            }
        } catch (JsonProcessingException exception) {
            throw new ToolException("MCP_INVALID_ARGUMENTS", "MCP tool arguments are not JSON serializable", false);
        }
    }

    private void ensureAvailable() {
        if (!properties.isEnabled()) {
            throw new ToolException("MCP_DISABLED", "MCP integration is disabled", false);
        }
        if (properties.getEndpoint().isBlank() || properties.getAllowedTools().isEmpty()) {
            throw new ToolException("MCP_NOT_CONFIGURED", "MCP endpoint and tool allowlist are required", false);
        }
    }

    private void clearSession() {
        sessionId = null;
        discoveredTools = null;
    }

    private static RestClient buildRestClient(McpProperties properties) {
        String endpoint = properties.getEndpoint().isBlank() ? "http://127.0.0.1" : properties.getEndpoint();
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid MCP endpoint", exception);
        }
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalStateException("MCP endpoint must use HTTP(S) and must not contain user info");
        }
        Duration connectTimeout = properties.getConnectTimeout() == null ? Duration.ofSeconds(2) : properties.getConnectTimeout();
        Duration readTimeout = properties.getReadTimeout() == null ? Duration.ofSeconds(10) : properties.getReadTimeout();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(uri.toString()).requestFactory(requestFactory).build();
    }
}
