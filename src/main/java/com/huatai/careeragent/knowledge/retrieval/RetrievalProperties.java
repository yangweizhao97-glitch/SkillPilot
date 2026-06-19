package com.huatai.careeragent.knowledge.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "career-agent.rag")
public class RetrievalProperties {
    private int defaultTopK = 8;
    private int maxTopK = 20;

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public int getMaxTopK() {
        return maxTopK;
    }

    public void setMaxTopK(int maxTopK) {
        this.maxTopK = maxTopK;
    }
}
