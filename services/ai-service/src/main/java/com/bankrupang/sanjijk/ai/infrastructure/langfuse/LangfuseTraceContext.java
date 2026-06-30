package com.bankrupang.sanjijk.ai.infrastructure.langfuse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LangfuseTraceContext {

    private static final long TTL_MS = 300_000; // 5분

    private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> timestamps = new ConcurrentHashMap<>();

    public void put(String traceId, String key, Object value) {
        store.computeIfAbsent(traceId, k -> new ConcurrentHashMap<>()).put(key, value);
        timestamps.put(traceId, System.currentTimeMillis());
    }

    public void addEvent(String traceId, String eventName, Map<String, Object> data) {
        Map<String, Object> ctx = store.computeIfAbsent(traceId, k -> new ConcurrentHashMap<>());
        timestamps.put(traceId, System.currentTimeMillis());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>)
                ctx.computeIfAbsent("_events", k -> Collections.synchronizedList(new ArrayList<>()));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", eventName);
        event.put("timestamp", Instant.now().toString());
        event.put("input", data);
        events.add(event);
    }

    public Map<String, Object> get(String traceId) {
        return store.getOrDefault(traceId, Collections.emptyMap());
    }

    public void remove(String traceId) {
        store.remove(traceId);
        timestamps.remove(traceId);
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        timestamps.entrySet().removeIf(e -> {
            if (now - e.getValue() > TTL_MS) {
                store.remove(e.getKey());
                return true;
            }
            return false;
        });
    }
}
