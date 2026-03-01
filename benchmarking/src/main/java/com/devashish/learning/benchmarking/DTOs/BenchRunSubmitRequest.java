package com.devashish.learning.benchmarking.DTOs;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record BenchRunSubmitRequest(
    UUID runid,
    @NotBlank(message = "language is required") String language
) {}
