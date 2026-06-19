package com.huatai.careeragent.knowledge.retrieval;

import com.huatai.careeragent.file.FileType;

public record ChunkSearchRow(
        Long chunkId,
        Long documentId,
        FileType sourceType,
        String sourceTitle,
        String sourceLocator,
        String content,
        double score
) {
}
