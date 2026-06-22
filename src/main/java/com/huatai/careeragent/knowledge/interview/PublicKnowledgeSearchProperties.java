package com.huatai.careeragent.knowledge.interview;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("career-agent.public-knowledge.search")
public class PublicKnowledgeSearchProperties {
    private boolean enabled;
    private String toolName = "search_web";
    private List<String> allowedDomains = List.of();
    private int maxResults = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName == null ? "" : toolName.trim(); }
    public List<String> getAllowedDomains() { return allowedDomains; }
    public void setAllowedDomains(List<String> allowedDomains) {
        this.allowedDomains = allowedDomains == null ? List.of() : allowedDomains.stream()
                .map(String::trim).map(String::toLowerCase).filter(value -> !value.isBlank()).distinct().toList();
    }
    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = Math.min(Math.max(maxResults, 1), 20); }
}
