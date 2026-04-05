package com.devashish.learning.benchmarking.DTOs;

import java.util.List;

public record ResumeRunRequest(
    List<String> languages,
    List<String> datasets,
    List<String> tools,
    Boolean failedOnly
) {}
