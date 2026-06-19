package com.huatai.careeragent.knowledge.embedding;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.knowledge.chunk.DocumentChunk;
import com.huatai.careeragent.knowledge.chunk.DocumentChunkRepository;
import com.huatai.careeragent.knowledge.embedding.EmbeddingDtos.EmbedDocumentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentEmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(DocumentEmbeddingService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingVectorFormatter vectorFormatter;
    private final EmbeddingRepository embeddingRepository;

    public DocumentEmbeddingService(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            EmbeddingClient embeddingClient,
            EmbeddingProperties embeddingProperties,
            EmbeddingVectorFormatter vectorFormatter,
            EmbeddingRepository embeddingRepository
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingClient = embeddingClient;
        this.embeddingProperties = embeddingProperties;
        this.vectorFormatter = vectorFormatter;
        this.embeddingRepository = embeddingRepository;
    }

    @Transactional
    public EmbedDocumentResponse embedDocument(Long userId, Long documentId) {
        if (!documentRepository.existsByIdAndUserId(documentId, userId)) {
            throw new BusinessException("DOCUMENT_NOT_FOUND", "Document not found", HttpStatus.NOT_FOUND);
        }

        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdAndUserIdOrderByChunkIndexAsc(documentId, userId);
        if (chunks.isEmpty()) {
            throw new BusinessException("DOCUMENT_CHUNKS_NOT_FOUND", "Document has no chunks to embed", HttpStatus.CONFLICT);
        }

        String model = null;
        for (DocumentChunk chunk : chunks) {
            long start = System.nanoTime();
            EmbeddingResponse response = embedWithRetry(chunk.getContent());
            validate(response);
            model = response.model();
            embeddingRepository.updateChunkEmbedding(chunk.getId(), vectorFormatter.toPgVector(response.vector()));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info(
                    "Embedded document chunk: documentId={}, chunkId={}, model={}, estimatedTokens={}, elapsedMs={}",
                    documentId,
                    chunk.getId(),
                    response.model(),
                    response.estimatedTokens(),
                    elapsedMs
            );
        }

        return new EmbedDocumentResponse(documentId, chunks.size(), model);
    }

    private EmbeddingResponse embedWithRetry(String content) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return embeddingClient.embed(content);
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("Embedding attempt failed: attempt={}, reason={}", attempt, e.getMessage());
            }
        }
        throw new BusinessException("EMBEDDING_FAILED", "Failed to create embedding", HttpStatus.BAD_GATEWAY);
    }

    private void validate(EmbeddingResponse response) {
        if (response == null || response.vector() == null) {
            throw new BusinessException("EMBEDDING_INVALID_RESPONSE", "Embedding provider returned invalid response", HttpStatus.BAD_GATEWAY);
        }
        if (response.vector().length != embeddingProperties.getEmbeddingDimension()) {
            throw new BusinessException("EMBEDDING_INVALID_DIMENSION", "Embedding provider returned invalid dimension", HttpStatus.BAD_GATEWAY);
        }
        for (float value : response.vector()) {
            if (!Float.isFinite(value)) {
                throw new BusinessException("EMBEDDING_INVALID_VECTOR", "Embedding provider returned invalid vector", HttpStatus.BAD_GATEWAY);
            }
        }
    }
}
