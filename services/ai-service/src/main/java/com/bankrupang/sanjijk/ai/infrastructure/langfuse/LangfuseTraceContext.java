package com.bankrupang.sanjijk.ai.infrastructure.langfuse;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LangfuseTraceContext {

    private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    public void put(String traceId, String key, Object value) {
        store.computeIfAbsent(traceId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public void addEvent(String traceId, String eventName, Map<String, Object> input) {
        Map<String, Object> ctx = store.computeIfAbsent(traceId, k -> new ConcurrentHashMap<>());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>)
                ctx.computeIfAbsent("_events", k -> Collections.synchronizedList(new ArrayList<>()));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", eventName);
        event.put("timestamp", Instant.now().toString());
        event.put("input", input);
        events.add(event);
    }

    public Map<String, Object> get(String traceId) {
        return store.getOrDefault(traceId, Collections.emptyMap());
    }

    public void remove(String traceId) {
        store.remove(traceId);
    }
}
