package com.huatai.careeragent.knowledge.chunk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findByDocumentIdAndUserIdOrderByChunkIndexAsc(Long documentId, Long userId);

    void deleteByDocumentIdAndUserId(Long documentId, Long userId);
}
