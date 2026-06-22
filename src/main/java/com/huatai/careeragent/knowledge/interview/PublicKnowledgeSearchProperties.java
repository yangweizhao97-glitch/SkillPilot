package com.huatai.careeragent.knowledge.interview;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("career-agent.public-knowledge.search")
public class PublicKnowledgeSearchProperties {
    private boolean enabled;
    private String toolName = "search_web";
    private String fetchToolName = "fetch_page";
    private List<String> allowedDomains = List.of();
    private int maxResults = 10;
    private int maxContentChars = 20000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName == null ? "" : toolName.trim(); }
    public String getFetchToolName() { return fetchToolName; }
    public void setFetchToolName(String fetchToolName) {
        this.fetchToolName = fetchToolName == null ? "" : fetchToolName.trim();
    }
    public List<String> getAllowedDomains() { return allowedDomains; }
    public void setAllowedDomains(List<String> allowedDomains) {
        this.allowedDomains = allowedDomains == null ? List.of() : allowedDomains.stream()
                .map(String::trim).map(String::toLowerCase).filter(value -> !value.isBlank()).distinct().toList();
    }
    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = Math.min(Math.max(maxResults, 1), 20); }
    public int getMaxContentChars() { return maxContentChars; }
    public void setMaxContentChars(int maxContentChars) {
        this.maxContentChars = Math.min(Math.max(maxContentChars, 1000), 50000);
    }
}
