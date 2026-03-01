package com.devashish.learning.benchmarking.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobStatusResponse(
    UUID runId,
    String requestedLanguage,
    JobStatus status,
    int totalTasks,
    int pendingTasks,
    int runningTasks,
    int successTasks,
    int failedTasks,
    int timeoutTasks,
    int skippedTasks,
    Instant submittedAt,
    Instant startedAt,
    Instant completedAt,
    List<TaskProgressView> tasks
) {}
