package com.huatai.careeragent.knowledge.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmbeddingRepository {
    private final JdbcTemplate jdbcTemplate;

    public EmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateChunkEmbedding(Long chunkId, String vectorLiteral) {
        jdbcTemplate.update("UPDATE document_chunks SET embedding = ?::vector WHERE id = ?", vectorLiteral, chunkId);
    }
}
