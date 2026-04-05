package com.devashish.learning.benchmarking.models;

import java.time.Instant;
import java.util.UUID;

public record BenchmarkRunSummaryResponse(
    UUID runId,
    String requestedLanguage,
    JobStatus status,
    boolean archived,
    int totalTasks,
    int pendingTasks,
    int runningTasks,
    int successTasks,
    int failedTasks,
    int timeoutTasks,
    int skippedTasks,
    int cancelledTasks,
    Instant submittedAt,
    Instant startedAt,
    Instant completedAt,
    Instant archivedAt
) {}
