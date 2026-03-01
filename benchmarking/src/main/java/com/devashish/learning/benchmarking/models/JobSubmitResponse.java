package com.devashish.learning.benchmarking.models;

import java.time.Instant;
import java.util.UUID;

public record JobSubmitResponse(
    UUID runId,
    JobStatus status,
    int totalTasks,
    Instant submittedAt
) {}
