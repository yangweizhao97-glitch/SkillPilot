package com.huatai.careeragent.knowledge.embedding;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.knowledge.embedding.EmbeddingDtos.EmbedDocumentResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}/embeddings")
public class DocumentEmbeddingController {
    private final DocumentEmbeddingService documentEmbeddingService;

    public DocumentEmbeddingController(DocumentEmbeddingService documentEmbeddingService) {
        this.documentEmbeddingService = documentEmbeddingService;
    }

    @PostMapping
    public ApiResponse<EmbedDocumentResponse> embedDocument(CurrentUser currentUser, @PathVariable Long documentId) {
        return ApiResponse.ok(documentEmbeddingService.embedDocument(currentUser.userId(), documentId));
    }
}
