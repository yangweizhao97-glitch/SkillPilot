package com.huatai.careeragent.agent.core;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AgentRetrySleeper {
    public void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AgentException("AGENT_RETRY_INTERRUPTED", "Agent retry interrupted", false, exception);
        }
    }
}
