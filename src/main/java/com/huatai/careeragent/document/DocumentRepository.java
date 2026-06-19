package com.huatai.careeragent.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByFileIdAndUserId(Long fileId, Long userId);
}
