package com.huatai.careeragent.agent.handoff;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("career-agent.agent.handoff")
public class HandoffProperties {
    private boolean enabled = true;
    private int maxDepth = 4;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
}
