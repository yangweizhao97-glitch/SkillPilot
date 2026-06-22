package com.huatai.careeragent.knowledge.interview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicKnowledgeEmbeddingRepository {
    private final JdbcTemplate jdbcTemplate;

    public PublicKnowledgeEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void update(Long questionId, String vector) {
        jdbcTemplate.update("UPDATE public_interview_questions SET embedding = ?::vector, updated_at = NOW() WHERE id = ?",
                vector, questionId);
    }

    public boolean allEmbedded(Long sourceId) {
        Integer missing = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM public_interview_questions q
                JOIN interview_experiences e ON e.id = q.experience_id
                WHERE e.source_id = ? AND q.embedding IS NULL
                """, Integer.class, sourceId);
        return missing != null && missing == 0;
    }
}
