package com.huatai.careeragent.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "career-agent.llm")
public class ModelConfig {
    private String provider = "DASHSCOPE";
    private String dashscopeApiKey = "";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String chatModel = "qwen-flash";
    private String embeddingModel = "text-embedding-v4";
    private int embeddingDimension = 1024;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(60);
    private int maxRetries = 2;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getDashscopeApiKey() { return dashscopeApiKey; }
    public void setDashscopeApiKey(String dashscopeApiKey) { this.dashscopeApiKey = dashscopeApiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int embeddingDimension) { this.embeddingDimension = embeddingDimension; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = Math.max(maxRetries, 0); }
}
