package com.huatai.careeragent.knowledge.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, Long> {
    Optional<KnowledgeSource> findByContentHash(String contentHash);
    Optional<KnowledgeSource> findBySourceUrlIgnoreCase(String sourceUrl);
}
