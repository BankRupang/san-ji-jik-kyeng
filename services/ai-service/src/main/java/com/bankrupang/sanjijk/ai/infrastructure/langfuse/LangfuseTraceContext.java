package com.bankrupang.sanjijk.ai.infrastructure.langfuse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LangfuseTraceContext {

    private static final long TTL_MS = 300_000; // 5분

    private static class TraceEntry {
        final Map<String, Object> data = new ConcurrentHashMap<>();
        final List<TraceEvent> events = new CopyOnWriteArrayList<>();
        volatile long lastModified = System.currentTimeMillis();
    }

    private final ConcurrentHashMap<String, TraceEntry> store = new ConcurrentHashMap<>();

    public void put(String traceId, String key, Object value) {
        if (key == null || value == null) return;
        TraceEntry entry = store.computeIfAbsent(traceId, k -> new TraceEntry());
        entry.data.put(key, value);
        entry.lastModified = System.currentTimeMillis();
    }

    public void addEvent(String traceId, String eventName, Map<String, Object> data) {
        TraceEntry entry = store.computeIfAbsent(traceId, k -> new TraceEntry());
        entry.lastModified = System.currentTimeMillis();
        entry.events.add(new TraceEvent(eventName, Instant.now().toString(), data));
    }

    public Map<String, Object> get(String traceId) {
        TraceEntry entry = store.get(traceId);
        return entry != null ? Collections.unmodifiableMap(entry.data) : Collections.emptyMap();
    }

    public List<TraceEvent> getEvents(String traceId) {
        TraceEntry entry = store.get(traceId);
        return entry != null ? Collections.unmodifiableList(entry.events) : Collections.emptyList();
    }

    public void remove(String traceId) {
        store.remove(traceId);
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now - e.getValue().lastModified > TTL_MS);
    }
}
