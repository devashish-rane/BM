package com.devashish.learning.benchmarking.models;

import java.util.List;

public record PagedBenchmarkRunsResponse(
    List<BenchmarkRunSummaryResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {}
