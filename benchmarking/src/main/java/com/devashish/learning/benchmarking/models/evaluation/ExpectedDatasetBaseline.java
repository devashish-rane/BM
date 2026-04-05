package com.devashish.learning.benchmarking.models.evaluation;

public record ExpectedDatasetBaseline(
    String language,
    String dataset,
    String sourceRepoUrl,
    String defaultBranch,
    int expectedFindings,
    String expectedResultVersion
) {}
