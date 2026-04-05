package com.devashish.learning.benchmarking.models;

public record ToolExecutionRequest(
    String language,
    String dataset,
    String tool
) {}
