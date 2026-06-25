package com.bankrupang.sanjijk.ai.infrastructure.langfuse;

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
    private static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");

    private final RestClient restClient;
    private final boolean enabled;

    public LangfuseSpanExporter(
            @Value("${langfuse.host:http://localhost:3001}") String host,
            @Value("${langfuse.auth-header:}") String authHeader) {
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

            // trace-create: 같은 traceId로 여러 번 보내면 Langfuse가 merge함
            // chat span의 input/output이 있으면 trace 레벨에도 반영
            String prompt = span.getAttributes().get(GEN_AI_PROMPT);
            String completion = span.getAttributes().get(GEN_AI_COMPLETION);
            Map<String, Object> traceBody = new LinkedHashMap<>();
            traceBody.put("id", traceId);
            traceBody.put("name", span.getName());
            if (prompt != null) traceBody.put("input", prompt);
            if (completion != null) traceBody.put("output", completion);
            batch.add(buildEvent("trace-create", startTime, traceBody));

            // generation-create: 실제 LLM 호출 상세 기록
            Map<String, Object> genBody = new LinkedHashMap<>();
            genBody.put("id", spanId);
            genBody.put("traceId", traceId);
            genBody.put("name", span.getName());
            genBody.put("startTime", startTime.toString());
            genBody.put("endTime", endTime.toString());
            genBody.put("model", span.getAttributes().get(GEN_AI_REQUEST_MODEL));
            genBody.put("input", span.getAttributes().get(GEN_AI_PROMPT));
            genBody.put("output", span.getAttributes().get(GEN_AI_COMPLETION));

            Long inputTokens = span.getAttributes().get(GEN_AI_USAGE_INPUT_TOKENS);
            Long outputTokens = span.getAttributes().get(GEN_AI_USAGE_OUTPUT_TOKENS);
            if (inputTokens != null || outputTokens != null) {
                long in = inputTokens != null ? inputTokens : 0L;
                long out = outputTokens != null ? outputTokens : 0L;
                genBody.put("usage", Map.of("input", in, "output", out, "total", in + out));
            }
            batch.add(buildEvent("generation-create", startTime, genBody));
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
