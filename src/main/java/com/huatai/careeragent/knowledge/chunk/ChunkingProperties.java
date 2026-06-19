package com.huatai.careeragent.knowledge.chunk;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "career-agent.chunking")
public class ChunkingProperties {
    private int targetTokens = 700;
    private int overlapTokens = 100;

    public int getTargetTokens() {
        return targetTokens;
    }

    public void setTargetTokens(int targetTokens) {
        this.targetTokens = targetTokens;
    }

    public int getOverlapTokens() {
        return overlapTokens;
    }

    public void setOverlapTokens(int overlapTokens) {
        this.overlapTokens = overlapTokens;
    }

    public ChunkingOptions toOptions() {
        return new ChunkingOptions(targetTokens, overlapTokens);
    }
}
