package com.huatai.careeragent.knowledge.chunk;

import com.huatai.careeragent.file.FileType;

public record TextChunk(
        int chunkIndex,
        FileType sourceType,
        String sourceTitle,
        String sourceLocator,
        String content,
        int tokenCount
) {
}
