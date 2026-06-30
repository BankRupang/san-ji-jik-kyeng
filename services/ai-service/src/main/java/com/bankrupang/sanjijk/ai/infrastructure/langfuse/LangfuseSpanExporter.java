package com.bankrupang.sanjijk.ai.infrastructure.langfuse;

import com.bankrupang.sanjijk.ai.infrastructure.config.ChatClientConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
public class LangfuseSpanExporter implements SpanExporter {

    private static final AttributeKey<String> GEN_AI_PROMPT = AttributeKey.stringKey("gen_ai.prompt");
    private static final AttributeKey<String> GEN_AI_COMPLETION = AttributeKey.stringKey("gen_ai.completion");
    private static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<String> GEN_AI_USAGE_INPUT_TOKENS = AttributeKey.stringKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<String> GEN_AI_USAGE_OUTPUT_TOKENS = AttributeKey.stringKey("gen_ai.usage.output_tokens");

    private final RestClient restClient;
    private final LangfuseTraceContext traceContext;
    private final boolean enabled;

    public LangfuseSpanExporter(
            @Value("${langfuse.host:http://localhost:3001}") String host,
            @Value("${langfuse.auth-header:}") String authHeader,
            LangfuseTraceContext traceContext) {
        this.traceContext = traceContext;
        this.enabled = !authHeader.isBlank();
        if (enabled) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(3_000);
            requestFactory.setReadTimeout(5_000);
            this.restClient = RestClient.builder()
                    .baseUrl(host)
                    .defaultHeader("Authorization", "Basic " + authHeader)
                    .requestFactory(requestFactory)
                    .build();
        } else {
            this.restClient = null;
            log.info("Langfuse auth-header가 설정되지 않아 트레이싱이 비활성화됩니다.");
        }
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (!enabled) return CompletableResultCode.ofSuccess();

        List<Map<String, Object>> batch = new ArrayList<>();

        for (SpanData span : spans) {
            if (span.getAttributes().get(GEN_AI_REQUEST_MODEL) == null) continue;

            String traceId = span.getTraceId();
            String spanId = span.getSpanId();
            Instant startTime = Instant.ofEpochSecond(0, span.getStartEpochNanos());
            Instant endTime = Instant.ofEpochSecond(0, span.getEndEpochNanos());

            String prompt = span.getAttributes().get(GEN_AI_PROMPT);
            String completion = span.getAttributes().get(GEN_AI_COMPLETION);

            // 응답 생성 스팬은 [참고 문서] 마커 포함, reformulation은 미포함
            boolean isReformulation = prompt == null || !prompt.contains(ChatClientConfig.DOCUMENT_CONTEXT_MARKER);
            String generationName = isReformulation ? "query.reformulation" : "response.generation";

            Map<String, Object> ctx = traceContext.get(traceId);

            // trace-create: userId, sessionId 포함 (같은 traceId로 여러 번 보내면 Langfuse가 merge)
            Map<String, Object> traceBody = new LinkedHashMap<>();
            traceBody.put("id", traceId);
            traceBody.put("name", "chat.pipeline");
            if (ctx.containsKey("user_id")) traceBody.put("userId", ctx.get("user_id"));
            if (ctx.containsKey("session_id")) traceBody.put("sessionId", ctx.get("session_id"));
            if (prompt != null) traceBody.put("input", prompt);
            if (completion != null) traceBody.put("output", completion);
            // response.generation 시점에만 metadata 추가 (이때 search 데이터 확보됨, Langfuse가 merge)
            if (!isReformulation && !ctx.isEmpty()) {
                Map<String, Object> traceMetadata = new LinkedHashMap<>();
                if (ctx.containsKey("is_multi_turn")) traceMetadata.put("is_multi_turn", ctx.get("is_multi_turn"));
                if (ctx.containsKey("search_query")) traceMetadata.put("search_query", ctx.get("search_query"));
                if (ctx.containsKey("search_result_count")) traceMetadata.put("search_result_count", ctx.get("search_result_count"));
                if (ctx.containsKey("search_max_similarity")) traceMetadata.put("search_max_similarity", ctx.get("search_max_similarity"));
                if (!traceMetadata.isEmpty()) traceBody.put("metadata", traceMetadata);
            }
            batch.add(buildEvent("trace-create", startTime, traceBody));

            // generation-create: 실제 LLM 호출 상세 기록
            Map<String, Object> genBody = new LinkedHashMap<>();
            genBody.put("id", spanId);
            genBody.put("traceId", traceId);
            genBody.put("name", generationName);
            genBody.put("startTime", startTime.toString());
            genBody.put("endTime", endTime.toString());
            genBody.put("model", span.getAttributes().get(GEN_AI_REQUEST_MODEL));
            genBody.put("input", prompt);
            genBody.put("output", completion);

            // 토큰 사용량
            String inputTokensStr = span.getAttributes().get(GEN_AI_USAGE_INPUT_TOKENS);
            String outputTokensStr = span.getAttributes().get(GEN_AI_USAGE_OUTPUT_TOKENS);
            if (inputTokensStr != null || outputTokensStr != null) {
                long in = 0L, out = 0L;
                try {
                    if (inputTokensStr != null) in = Long.parseLong(inputTokensStr);
                    if (outputTokensStr != null) out = Long.parseLong(outputTokensStr);
                } catch (NumberFormatException ignored) {}
                genBody.put("usage", Map.of("input", in, "output", out, "total", in + out));
            }

            // metadata: 멀티턴 여부 + 검색 결과 수 / 유사도 (response.generation에만)
            if (!ctx.isEmpty()) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (ctx.containsKey("is_multi_turn")) metadata.put("is_multi_turn", ctx.get("is_multi_turn"));
                if (!isReformulation) {
                    if (ctx.containsKey("search_query")) metadata.put("search_query", ctx.get("search_query"));
                    if (ctx.containsKey("search_result_count")) metadata.put("search_result_count", ctx.get("search_result_count"));
                    if (ctx.containsKey("search_max_similarity")) metadata.put("search_max_similarity", ctx.get("search_max_similarity"));
                }
                if (!metadata.isEmpty()) genBody.put("metadata", metadata);
            }
            batch.add(buildEvent("generation-create", startTime, genBody));

            // 빈 검색 결과 / 검색 실패 이벤트 (response.generation 이후에만 emit 후 컨텍스트 정리)
            if (!isReformulation && !ctx.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> events = (List<Map<String, Object>>) ctx.get("_events");
                if (events != null) {
                    for (Map<String, Object> event : events) {
                        Map<String, Object> eventBody = new LinkedHashMap<>();
                        eventBody.put("id", UUID.randomUUID().toString());
                        eventBody.put("traceId", traceId);
                        eventBody.put("name", event.get("name"));
                        eventBody.put("startTime", event.getOrDefault("timestamp", endTime.toString()));
                        eventBody.put("input", event.get("input"));
                        batch.add(buildEvent("event-create", startTime, eventBody));
                    }
                }
                traceContext.remove(traceId);
            }
        }

        if (batch.isEmpty()) return CompletableResultCode.ofSuccess();

        try {
            restClient.post()
                    .uri("/api/public/ingestion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("batch", batch))
                    .retrieve()
                    .toBodilessEntity();
            return CompletableResultCode.ofSuccess();
        } catch (Exception e) {
            log.warn("Langfuse 전송 실패: {}", e.getMessage());
            return CompletableResultCode.ofFailure();
        }
    }

    private Map<String, Object> buildEvent(String type, Instant timestamp, Map<String, Object> body) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("type", type);
        event.put("timestamp", timestamp.toString());
        event.put("body", body);
        return event;
    }

    @Override
    public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }

    @Override
    public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
}
