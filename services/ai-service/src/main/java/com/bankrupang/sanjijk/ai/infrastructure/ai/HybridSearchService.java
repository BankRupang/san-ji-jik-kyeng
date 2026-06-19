package com.bankrupang.sanjijk.ai.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    @Value("${ai.chat.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${ai.chat.top-k:3}")
    private int topK;

    // RRF 상수 k=60 (Reciprocal Rank Fusion 표준값)
    private static final String HYBRID_SEARCH_SQL = """
            WITH vector_results AS (
                SELECT id, content,
                       ROW_NUMBER() OVER (ORDER BY embedding <=> CAST(? AS vector)) AS rank
                FROM vector_store
                WHERE 1 - (embedding <=> CAST(? AS vector)) >= ?
                LIMIT ?
            ),
            text_results AS (
                SELECT id, content,
                       ROW_NUMBER() OVER (
                           ORDER BY ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', ?)) DESC
                       ) AS rank
                FROM vector_store
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
            SELECT content FROM rrf_scores ORDER BY rrf_score DESC LIMIT ?
            """;

    public List<String> search(String query) {
        try {
            float[] embedding = embeddingModel.embed(query);
            String vectorStr = toVectorString(embedding);
            int searchLimit = topK * 2;

            return jdbcTemplate.queryForList(HYBRID_SEARCH_SQL, String.class,
                    vectorStr, vectorStr, similarityThreshold, searchLimit,
                    query, query, searchLimit,
                    topK);
        } catch (Exception e) {
            log.warn("하이브리드 검색 실패 - query: {}, error: {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        return sb.append("]").toString();
    }
}
