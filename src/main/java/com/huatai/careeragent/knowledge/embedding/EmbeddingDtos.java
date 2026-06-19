package com.huatai.careeragent.knowledge.embedding;

public final class EmbeddingDtos {
    private EmbeddingDtos() {
    }

    public record EmbedDocumentResponse(Long documentId, int embeddedChunkCount, String model) {
    }
}
