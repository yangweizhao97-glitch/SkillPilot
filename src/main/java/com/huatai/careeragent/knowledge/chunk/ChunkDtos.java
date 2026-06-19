package com.huatai.careeragent.knowledge.chunk;

import com.huatai.careeragent.file.FileType;

import java.util.List;

public final class ChunkDtos {
    private ChunkDtos() {
    }

    public record ChunkResponse(
            Long chunkId,
            int chunkIndex,
            FileType sourceType,
            String sourceTitle,
            String sourceLocator,
            String content,
            int tokenCount
    ) {
        public static ChunkResponse from(DocumentChunk chunk) {
            return new ChunkResponse(
                    chunk.getId(),
                    chunk.getChunkIndex(),
                    chunk.getSourceType(),
                    chunk.getSourceTitle(),
                    chunk.getSourceLocator(),
                    chunk.getContent(),
                    chunk.getTokenCount()
            );
        }
    }

    public record ChunkDocumentResponse(Long documentId, int chunkCount, List<ChunkResponse> chunks) {
    }
}
