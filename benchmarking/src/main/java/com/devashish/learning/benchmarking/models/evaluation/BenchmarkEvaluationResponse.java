package com.devashish.learning.benchmarking.models.evaluation;

import java.util.Map;

public record BenchmarkEvaluationResponse(
    Map<String, Map<String, DatasetEvaluationResponse>> evaluations
) {}
