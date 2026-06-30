package com.bankrupang.sanjijk.ai.infrastructure.persistence;

import com.bankrupang.sanjijk.ai.presentation.dto.response.DocumentInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
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

    public SearchResult hybridSearchWithStats(String vectorStr, String query, double similarityThreshold, int searchLimit, int topK) {
        return jdbcTemplate.query("""
                WITH pre_filter AS (
                    SELECT id, content, 1 - (embedding <=> CAST(? AS vector)) AS similarity
                    FROM ai_schema.vector_store
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT ?
                ),
                vector_results AS (
                    SELECT id, content,
                           ROW_NUMBER() OVER (ORDER BY similarity DESC) AS rank
                    FROM pre_filter
                    WHERE similarity >= ?
                ),
                text_results AS (
                    SELECT id, content,
                           ROW_NUMBER() OVER (
                               ORDER BY ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', ?)) DESC
                           ) AS rank
                    FROM ai_schema.vector_store
                    WHERE to_tsvector('simple', content) @@ plainto_tsquery('simple', ?)
                    LIMIT ?
                ),
                rrf_scores AS (
                    SELECT
                        COALESCE(v.id, t.id) AS id,
                        COALESCE(v.content, t.content) AS content,
                        COALESCE(1.0 / (60 + v.rank), 0.0) + COALESCE(1.0 / (60 + t.rank), 0.0) AS rrf_score
                    FROM vector_results v
                    FULL OUTER JOIN text_results t ON v.id = t.id
                )
                SELECT content, (SELECT COALESCE(MAX(similarity), 0.0) FROM pre_filter) AS max_similarity
                FROM rrf_scores ORDER BY rrf_score DESC LIMIT ?
                """,
                rs -> {
                    List<String> contents = new ArrayList<>();
                    double maxSimilarity = 0.0;
                    while (rs.next()) {
                        contents.add(rs.getString("content"));
                        maxSimilarity = rs.getDouble("max_similarity");
                    }
                    return new SearchResult(contents, maxSimilarity);
                },
                vectorStr, vectorStr, searchLimit,
                similarityThreshold,
                query, query, searchLimit,
                topK);
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
