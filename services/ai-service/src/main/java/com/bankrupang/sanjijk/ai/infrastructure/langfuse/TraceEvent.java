package com.bankrupang.sanjijk.ai.infrastructure.langfuse;

import java.util.Map;

record TraceEvent(String name, String timestamp, Map<String, Object> input) {}
