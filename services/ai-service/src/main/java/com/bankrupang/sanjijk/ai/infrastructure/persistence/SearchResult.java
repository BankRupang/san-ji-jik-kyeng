package com.bankrupang.sanjijk.ai.infrastructure.persistence;

import java.util.List;

public record SearchResult(List<String> contents, double maxSimilarity) {}
