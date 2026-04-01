package com.devashish.learning.benchmarking.models.evaluation;

public record ToolEvaluationResponse(
    String tool,
    int expectedFindings,
    int detectedFindings,
    int matchedFindings,
    double precision,
    double recall,
    double f1Score,
    String sourceRepoUrl,
    String expectedResultVersion,
    String weekOverWeekChartUrl
) {}
