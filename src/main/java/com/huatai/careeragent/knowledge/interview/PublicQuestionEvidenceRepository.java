package com.huatai.careeragent.knowledge.interview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicQuestionEvidenceRepository {
    private final JdbcTemplate jdbcTemplate;
    public PublicQuestionEvidenceRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void record(String questionHash, Long sourceId) {
        jdbcTemplate.update("""
                INSERT INTO public_question_evidence(question_hash, source_id)
                VALUES (?, ?) ON CONFLICT DO NOTHING
                """, questionHash, sourceId);
    }

    public int sourceCount(String questionHash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_question_evidence WHERE question_hash = ?",
                Integer.class, questionHash);
        return count == null ? 0 : count;
    }

    public boolean allAccepted(Long sourceId, int minimumScore) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM public_interview_questions q
                JOIN interview_experiences e ON e.id = q.experience_id
                WHERE e.source_id = ? AND (q.quality_status <> 'ACCEPTED' OR q.quality_score < ?)
                """, Integer.class, sourceId, minimumScore);
        return count != null && count == 0;
    }
}
