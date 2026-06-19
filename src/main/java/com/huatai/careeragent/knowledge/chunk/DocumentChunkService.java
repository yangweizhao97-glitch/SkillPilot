package com.huatai.careeragent.knowledge.chunk;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.knowledge.chunk.ChunkDtos.ChunkDocumentResponse;
import com.huatai.careeragent.knowledge.chunk.ChunkDtos.ChunkResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentChunkService {
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final TextChunker textChunker;
    private final ChunkingProperties chunkingProperties;

    public DocumentChunkService(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            TextChunker textChunker,
            ChunkingProperties chunkingProperties
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.textChunker = textChunker;
        this.chunkingProperties = chunkingProperties;
    }

    @Transactional
    public ChunkDocumentResponse chunkDocument(Long userId, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException("DOCUMENT_NOT_FOUND", "Document not found", HttpStatus.NOT_FOUND));

        List<TextChunk> chunks = textChunker.chunk(
                new ChunkSource(document.getDocType(), document.getTitle(), document.getContentText()),
                chunkingProperties.toOptions()
        );

        documentChunkRepository.deleteByDocumentIdAndUserId(documentId, userId);
        documentChunkRepository.flush();
        List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(
                chunks.stream().map(chunk -> new DocumentChunk(userId, documentId, chunk)).toList()
        );

        List<ChunkResponse> responses = savedChunks.stream()
                .map(ChunkResponse::from)
                .toList();
        return new ChunkDocumentResponse(documentId, responses.size(), responses);
    }

    @Transactional(readOnly = true)
    public ChunkDocumentResponse listChunks(Long userId, Long documentId) {
        if (!documentRepository.existsByIdAndUserId(documentId, userId)) {
            throw new BusinessException("DOCUMENT_NOT_FOUND", "Document not found", HttpStatus.NOT_FOUND);
        }
        List<ChunkResponse> chunks = documentChunkRepository.findByDocumentIdAndUserIdOrderByChunkIndexAsc(documentId, userId)
                .stream()
                .map(ChunkResponse::from)
                .toList();
        return new ChunkDocumentResponse(documentId, chunks.size(), chunks);
    }
}
