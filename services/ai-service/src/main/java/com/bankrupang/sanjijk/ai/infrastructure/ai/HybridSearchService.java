package com.bankrupang.sanjijk.ai.infrastructure.ai;

import com.bankrupang.sanjijk.ai.infrastructure.persistence.VectorStoreRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class HybridSearchService {

    private final VectorStoreRepository vectorStoreRepository;
    private final EmbeddingModel embeddingModel;
    private final ObservationRegistry observationRegistry;
    private final Counter emptySearchCounter;
    private final Counter searchFailCounter;

    public HybridSearchService(VectorStoreRepository vectorStoreRepository, EmbeddingModel embeddingModel,
                                ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.vectorStoreRepository = vectorStoreRepository;
        this.embeddingModel = embeddingModel;
        this.observationRegistry = observationRegistry;
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
        try {
            String prefixedQuery = "task: question answering | query: " + query;
            float[] embedding = embeddingModel.embed(prefixedQuery);
            String vectorStr = toVectorString(embedding);
            int searchLimit = topK * 2;

            List<String> results = vectorStoreRepository.hybridSearch(vectorStr, query, similarityThreshold, searchLimit, topK);

            if (results.isEmpty()) {
                emptySearchCounter.increment();
            }
            log.info("하이브리드 검색 결과 - query: {}, threshold: {}, 결과 수: {}", query, similarityThreshold, results.size());
            return results;
        } catch (Exception e) {
            searchFailCounter.increment();
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
