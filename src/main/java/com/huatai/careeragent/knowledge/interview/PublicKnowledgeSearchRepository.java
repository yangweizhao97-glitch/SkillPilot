package com.huatai.careeragent.knowledge.interview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PublicKnowledgeSearchRepository {
    private static final String SELECT = """
            SELECT q.id, q.question_hash, q.normalized_question, q.question_type, q.difficulty,
                   q.knowledge_points::text, q.answer_outline::text, q.reference_answer,
                   q.scoring_rubric::text, q.common_mistakes::text, q.follow_up_candidates::text,
                   e.industry, e.company, e.position, e.experience_level, e.interview_round, e.event_date,
                   s.title, s.source_url, s.platform, s.collected_at, s.quality_score::double precision,
            """;
    private static final String FROM = """
             FROM public_interview_questions q
             JOIN interview_experiences e ON e.id = q.experience_id
             JOIN knowledge_sources s ON s.id = e.source_id
             WHERE q.status = 'PUBLISHED' AND e.status = 'PUBLISHED' AND s.review_status = 'APPROVED'
            """;

    private final JdbcTemplate jdbcTemplate;

    public PublicKnowledgeSearchRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<PublicKnowledgeSearchRow> vector(String vector, PublicKnowledgeSearchDtos.SearchRequest request,
                                                 int limit) {
        StringBuilder sql = new StringBuilder(SELECT).append("""
                (GREATEST(0, 1 - (q.embedding <=> ?::vector)) * 0.80
                 + s.quality_score::double precision * 0.15
                 + (1.0 / (1.0 + GREATEST(0, EXTRACT(EPOCH FROM (NOW() - COALESCE(s.published_at, s.collected_at))) / 31557600))) * 0.05) AS score
                """).append(FROM).append(" AND q.embedding IS NOT NULL");
        List<Object> params = new ArrayList<>();
        params.add(vector);
        filters(sql, params, request);
        sql.append(" ORDER BY score DESC, q.id ASC LIMIT ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), this::map, params.toArray());
    }

    public List<PublicKnowledgeSearchRow> keyword(List<String> keywords,
                                                  PublicKnowledgeSearchDtos.SearchRequest request, int limit) {
        if (keywords.isEmpty()) return List.of();
        String searchable = "lower(concat_ws(' ', q.normalized_question, q.knowledge_points::text, e.summary, e.industry, e.company, e.position, e.interview_round))";
        StringBuilder sql = new StringBuilder(SELECT).append("(");
        List<Object> params = new ArrayList<>();
        for (int index = 0; index < keywords.size(); index++) {
            if (index > 0) sql.append(" + ");
            sql.append("CASE WHEN ").append(searchable).append(" LIKE ? THEN 1 ELSE 0 END");
            params.add("%" + keywords.get(index).toLowerCase() + "%");
        }
        sql.append(")::double precision / ? * 0.80 + s.quality_score::double precision * 0.15")
                .append(" + (1.0 / (1.0 + GREATEST(0, EXTRACT(EPOCH FROM (NOW() - COALESCE(s.published_at, s.collected_at))) / 31557600))) * 0.05 AS score")
                .append(FROM).append(" AND (");
        params.add(keywords.size());
        for (int index = 0; index < keywords.size(); index++) {
            if (index > 0) sql.append(" OR ");
            sql.append(searchable).append(" LIKE ?");
            params.add("%" + keywords.get(index).toLowerCase() + "%");
        }
        sql.append(")");
        filters(sql, params, request);
        sql.append(" ORDER BY score DESC, q.id ASC LIMIT ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), this::map, params.toArray());
    }

    private void filters(StringBuilder sql, List<Object> params, PublicKnowledgeSearchDtos.SearchRequest request) {
        exact(sql, params, "e.industry", request.industry());
        contains(sql, params, "e.position", request.position());
        contains(sql, params, "e.company", request.company());
        exact(sql, params, "e.experience_level", request.experienceLevel());
        exact(sql, params, "e.interview_round", request.interviewRound());
    }

    private void exact(StringBuilder sql, List<Object> params, String column, String value) {
        if (value == null || value.isBlank()) return;
        sql.append(" AND lower(").append(column).append(") = lower(?)");
        params.add(value.trim());
    }

    private void contains(StringBuilder sql, List<Object> params, String column, String value) {
        if (value == null || value.isBlank()) return;
        sql.append(" AND lower(").append(column).append(") LIKE lower(?)");
        params.add("%" + value.trim() + "%");
    }

    private PublicKnowledgeSearchRow map(ResultSet rs, int rowNum) throws SQLException {
        return new PublicKnowledgeSearchRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14),
                rs.getString(15), rs.getString(16), rs.getObject(17, java.time.LocalDate.class), rs.getString(18),
                rs.getString(19), rs.getString(20), rs.getTimestamp(21).toInstant(), rs.getDouble(22), rs.getDouble(23));
    }
}
