package com.devashish.learning.benchmarking.models;

import java.util.Map;

public record BenchmarkRunResponse(
    Map<String, Map<String, Map<String, String>>> results
) {}
