package com.bankrupang.sanjijk.ai.infrastructure.persistence;

import com.bankrupang.sanjijk.ai.presentation.dto.response.DocumentInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class VectorStoreRepository {

    private final JdbcTemplate jdbcTemplate;

    public int deleteBySource(String source) {
        return jdbcTemplate.update(
                "DELETE FROM ai_schema.vector_store WHERE metadata->>'source' = ?", source);
    }

    public int deleteBySourceExcludingVersion(String source, String version) {
        return jdbcTemplate.update(
                "DELETE FROM ai_schema.vector_store WHERE metadata->>'source' = ? AND (metadata->>'version' IS NULL OR metadata->>'version' != ?)",
                source, version);
    }

    public long countDistinctDocuments() {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT 1 FROM ai_schema.vector_store
                    GROUP BY metadata->>'title', metadata->>'source'
                ) t
                """, Long.class);
        return total == null ? 0L : total;
    }

    public List<DocumentInfoResponse> findDocuments(int limit, long offset) {
        return jdbcTemplate.query("""
                SELECT metadata->>'title' AS title,
                       metadata->>'source' AS source,
                       COUNT(*) AS chunk_count
                FROM ai_schema.vector_store
                GROUP BY metadata->>'title', metadata->>'source'
                ORDER BY metadata->>'source', metadata->>'title'
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> new DocumentInfoResponse(
                        rs.getString("title"),
                        rs.getString("source"),
                        rs.getInt("chunk_count")),
                limit, offset);
    }
}
