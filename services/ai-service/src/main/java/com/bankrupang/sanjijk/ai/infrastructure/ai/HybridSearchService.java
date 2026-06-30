package com.bankrupang.sanjijk.ai.infrastructure.ai;

import com.bankrupang.sanjijk.ai.infrastructure.persistence.SearchResult;
import com.bankrupang.sanjijk.ai.infrastructure.persistence.VectorStoreRepository;
import com.bankrupang.sanjijk.ai.infrastructure.langfuse.LangfuseTraceContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class HybridSearchService {

    private final VectorStoreRepository vectorStoreRepository;
    private final EmbeddingModel embeddingModel;
    private final ObservationRegistry observationRegistry;
    private final LangfuseTraceContext traceContext;
    private final Counter emptySearchCounter;
    private final Counter searchFailCounter;

    public HybridSearchService(VectorStoreRepository vectorStoreRepository, EmbeddingModel embeddingModel,
                                ObservationRegistry observationRegistry, MeterRegistry meterRegistry,
                                LangfuseTraceContext traceContext) {
        this.vectorStoreRepository = vectorStoreRepository;
        this.embeddingModel = embeddingModel;
        this.observationRegistry = observationRegistry;
        this.traceContext = traceContext;
        this.emptySearchCounter = Counter.builder("hybrid.search.empty")
                .description("검색 결과 없음 발생 횟수")
                .register(meterRegistry);
        this.searchFailCounter = Counter.builder("hybrid.search.fail")
                .description("하이브리드 검색 실패 횟수")
                .register(meterRegistry);
    }

    @Value("${ai.chat.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${ai.chat.top-k:3}")
    private int topK;

    public List<String> search(String query) {
        return Observation.createNotStarted("hybrid.search", observationRegistry)
                .observe(() -> doSearch(query));
    }

    private List<String> doSearch(String query) {
        String traceId = Span.current().getSpanContext().getTraceId();
        try {
            String prefixedQuery = "task: question answering | query: " + query;
            float[] embedding = embeddingModel.embed(prefixedQuery);
            String vectorStr = toVectorString(embedding);
            int searchLimit = topK * 2;

            SearchResult searchResult = vectorStoreRepository.hybridSearchWithStats(vectorStr, query, similarityThreshold, searchLimit, topK);
            List<String> results = Objects.requireNonNullElse(searchResult, new SearchResult(Collections.emptyList(), 0.0)).contents();
            double maxSimilarity = searchResult != null ? searchResult.maxSimilarity() : 0.0;

            traceContext.put(traceId, "search_query", query);
            traceContext.put(traceId, "search_result_count", results.size());
            traceContext.put(traceId, "search_max_similarity", maxSimilarity);

            if (results.isEmpty()) {
                emptySearchCounter.increment();
                traceContext.addEvent(traceId, "search_empty",
                        Map.of("query", query, "threshold", similarityThreshold));
            }
            return results;
        } catch (Exception e) {
            searchFailCounter.increment();
            traceContext.addEvent(traceId, "search_failed",
                    Map.of("query", query, "error", e.getMessage() != null ? e.getMessage() : "unknown"));
            log.warn("하이브리드 검색 실패 - query: {}, error: {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(Float.toString(embedding[i]));
        }
        return sb.append("]").toString();
    }
}
