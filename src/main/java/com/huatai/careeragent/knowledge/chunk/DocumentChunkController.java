package com.huatai.careeragent.knowledge.chunk;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.knowledge.chunk.ChunkDtos.ChunkDocumentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}/chunks")
public class DocumentChunkController {
    private final DocumentChunkService documentChunkService;

    public DocumentChunkController(DocumentChunkService documentChunkService) {
        this.documentChunkService = documentChunkService;
    }

    @PostMapping
    public ApiResponse<ChunkDocumentResponse> chunkDocument(CurrentUser currentUser, @PathVariable Long documentId) {
        return ApiResponse.ok(documentChunkService.chunkDocument(currentUser.userId(), documentId));
    }

    @GetMapping
    public ApiResponse<ChunkDocumentResponse> listChunks(CurrentUser currentUser, @PathVariable Long documentId) {
        return ApiResponse.ok(documentChunkService.listChunks(currentUser.userId(), documentId));
    }
}
