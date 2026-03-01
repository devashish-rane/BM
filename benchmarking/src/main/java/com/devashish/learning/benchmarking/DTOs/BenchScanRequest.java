package com.devashish.learning.benchmarking.DTOs;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record BenchScanRequest( @NotNull(message = "runid is required") UUID runid, 
    @NotBlank(message = "language is required") String language) {
}
