package com.huatai.careeragent.knowledge.embedding;

public record EmbeddingResponse(float[] vector, int estimatedTokens, String model) {
}
