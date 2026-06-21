package com.huatai.careeragent.mcp;

import com.huatai.careeragent.agent.tool.AgentNames;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties("career-agent.mcp")
public class McpProperties {
    private boolean enabled;
    private String endpoint = "";
    private String serverName = "default";
    private String protocolVersion = "2025-06-18";
    private String bearerToken = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(10);
    private int maxRequestBytes = 65_536;
    private int maxResponseBytes = 1_048_576;
    private List<String> allowedTools = List.of();
    private Set<String> allowedAgents = Set.of(
            AgentNames.JOB_MATCH_AGENT,
            AgentNames.RESUME_ANALYSIS_AGENT,
            AgentNames.INTERVIEW_QUESTION_AGENT
    );

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint == null ? "" : endpoint.trim(); }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName == null ? "default" : serverName.trim(); }
    public String getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion == null ? "" : protocolVersion.trim(); }
    public String getBearerToken() { return bearerToken; }
    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken == null ? "" : bearerToken.trim(); }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public void setMaxRequestBytes(int maxRequestBytes) { this.maxRequestBytes = maxRequestBytes; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
    public List<String> getAllowedTools() { return allowedTools; }
    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools == null ? List.of() : allowedTools.stream()
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }
    public Set<String> getAllowedAgents() { return allowedAgents; }
    public void setAllowedAgents(Set<String> allowedAgents) {
        this.allowedAgents = allowedAgents == null ? Set.of() : Set.copyOf(allowedAgents.stream()
                .map(String::trim).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
}
