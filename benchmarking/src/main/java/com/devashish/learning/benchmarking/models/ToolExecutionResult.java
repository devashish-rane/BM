package com.devashish.learning.benchmarking.models;

public record ToolExecutionResult(
    String language,
    String dataset,
    String tool,
    String result
) {}
