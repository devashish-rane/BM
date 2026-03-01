package com.devashish.learning.benchmarking.models;

import java.time.Instant;

public record TaskProgressView(
    String taskId,
    String language,
    String dataset,
    String tool,
    TaskStatus status,
    int attempts,
    Instant startedAt,
    Instant completedAt,
    String error
) {}
