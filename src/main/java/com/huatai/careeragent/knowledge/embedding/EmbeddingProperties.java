package com.huatai.careeragent.knowledge.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "career-agent.llm")
public class EmbeddingProperties {
    private String provider = "LOCAL";
    private String dashscopeApiKey = "";
    private String embeddingModel = "text-embedding-v4";
    private int embeddingDimension = 1024;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDashscopeApiKey() {
        return dashscopeApiKey;
    }

    public void setDashscopeApiKey(String dashscopeApiKey) {
        this.dashscopeApiKey = dashscopeApiKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }
}
