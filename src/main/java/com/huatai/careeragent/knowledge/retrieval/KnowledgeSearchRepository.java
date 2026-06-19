package com.huatai.careeragent.knowledge.retrieval;

import com.huatai.careeragent.file.FileType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class KnowledgeSearchRepository {
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChunkSearchRow> searchVector(Long userId, String queryVector, List<FileType> sourceTypes, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, document_id, source_type, source_title, source_locator, content,
                       GREATEST(0, 1 - (embedding <=> ?::vector)) AS score
                FROM document_chunks
                WHERE user_id = ? AND embedding IS NOT NULL
                """);
        List<Object> params = new ArrayList<>();
        params.add(queryVector);
        params.add(userId);
        appendSourceTypeFilter(sql, params, sourceTypes);
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        params.add(queryVector);
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapRow, params.toArray());
    }

    public List<ChunkSearchRow> searchKeyword(Long userId, List<String> keywords, List<FileType> sourceTypes, int limit) {
        if (keywords.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT id, document_id, source_type, source_title, source_locator, content,
                       (
                """);
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                sql.append(" + ");
            }
            sql.append("CASE WHEN lower(content) LIKE ? THEN 1 ELSE 0 END");
            params.add("%" + keywords.get(i).toLowerCase() + "%");
        }
        sql.append("""
                       )::double precision / ? AS score
                FROM document_chunks
                WHERE user_id = ?
                """);
        params.add(keywords.size());
        params.add(userId);
        appendSourceTypeFilter(sql, params, sourceTypes);
        sql.append(" AND (");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("lower(content) LIKE ?");
            params.add("%" + keywords.get(i).toLowerCase() + "%");
        }
        sql.append(") ORDER BY score DESC, id ASC LIMIT ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapRow, params.toArray());
    }

    private void appendSourceTypeFilter(StringBuilder sql, List<Object> params, List<FileType> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return;
        }
        sql.append(" AND source_type IN (");
        for (int i = 0; i < sourceTypes.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(sourceTypes.get(i).name());
        }
        sql.append(")");
    }

    private ChunkSearchRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ChunkSearchRow(
                rs.getLong("id"),
                rs.getLong("document_id"),
                FileType.valueOf(rs.getString("source_type")),
                rs.getString("source_title"),
                rs.getString("source_locator"),
                rs.getString("content"),
                rs.getDouble("score")
        );
    }
}
