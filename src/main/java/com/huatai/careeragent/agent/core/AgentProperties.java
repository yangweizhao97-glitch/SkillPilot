package com.huatai.careeragent.agent.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "career-agent.agent")
public class AgentProperties {
    private int maxRetries = 2;
    private Duration initialRetryDelay = Duration.ofMillis(100);

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = Math.max(maxRetries, 0); }
    public Duration getInitialRetryDelay() { return initialRetryDelay; }
    public void setInitialRetryDelay(Duration initialRetryDelay) { this.initialRetryDelay = initialRetryDelay; }
}
