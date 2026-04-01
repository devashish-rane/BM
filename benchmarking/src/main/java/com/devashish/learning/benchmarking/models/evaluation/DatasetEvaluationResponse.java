package com.devashish.learning.benchmarking.models.evaluation;

import java.util.Map;

public record DatasetEvaluationResponse(
    String sourceRepoUrl,
    String defaultBranch,
    int expectedFindings,
    String expectedResultVersion,
    Map<String, ToolEvaluationResponse> tools
) {}
