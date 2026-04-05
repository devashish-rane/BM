package com.devashish.learning.benchmarking.configs;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dataset-catalog")
public record DatasetCatalogConfig(
    Map<String, Map<String, DatasetDefinition>> language
) {

    public record DatasetDefinition(
        String sourceRepoUrl,
        String defaultBranch,
        Integer expectedFindings
    ) {}
}
