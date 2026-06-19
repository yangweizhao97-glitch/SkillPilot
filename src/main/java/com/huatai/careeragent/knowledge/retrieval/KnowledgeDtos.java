package com.huatai.careeragent.knowledge.retrieval;

import com.huatai.careeragent.file.FileType;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class KnowledgeDtos {
    private KnowledgeDtos() {
    }

    public record KnowledgeSearchRequest(
            @NotBlank String query,
            List<FileType> sourceTypes,
            Integer topK,
            RetrievalMode retrievalMode
    ) {
    }

    public record KnowledgeSearchResponse(List<KnowledgeSearchItem> items) {
    }

    public record KnowledgeSearchItem(
            String citationId,
            Long documentId,
            FileType sourceType,
            String sourceTitle,
            String sourceLocator,
            String content,
            double score
    ) {
    }
}
