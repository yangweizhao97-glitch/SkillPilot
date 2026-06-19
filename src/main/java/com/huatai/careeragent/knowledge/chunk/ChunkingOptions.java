package com.huatai.careeragent.knowledge.chunk;

public record ChunkingOptions(int targetTokens, int overlapTokens) {
    public ChunkingOptions {
        if (targetTokens < 1) {
            throw new IllegalArgumentException("targetTokens must be positive");
        }
        if (overlapTokens < 0) {
            throw new IllegalArgumentException("overlapTokens must not be negative");
        }
        if (overlapTokens >= targetTokens) {
            throw new IllegalArgumentException("overlapTokens must be smaller than targetTokens");
        }
    }
}
