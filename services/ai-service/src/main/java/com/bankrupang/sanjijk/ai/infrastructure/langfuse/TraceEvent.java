package com.bankrupang.sanjijk.ai.infrastructure.langfuse;

import java.util.Collections;
import java.util.Map;

record TraceEvent(String name, String timestamp, Map<String, Object> input) {
    TraceEvent {
        input = input != null ? Map.copyOf(input) : Collections.emptyMap();
    }
}
