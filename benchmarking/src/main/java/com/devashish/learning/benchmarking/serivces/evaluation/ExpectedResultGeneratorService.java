package com.devashish.learning.benchmarking.serivces.evaluation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.devashish.learning.benchmarking.configs.DatasetCatalogConfig;
import com.devashish.learning.benchmarking.models.evaluation.ExpectedDatasetBaseline;

@Service
public class ExpectedResultGeneratorService {

    private final DatasetCatalogConfig datasetCatalogConfig;

    public ExpectedResultGeneratorService(DatasetCatalogConfig datasetCatalogConfig) {
        this.datasetCatalogConfig = datasetCatalogConfig;
    }

    public ExpectedDatasetBaseline generate(String language, String dataset) {
        Map<String, Map<String, DatasetCatalogConfig.DatasetDefinition>> catalog = datasetCatalogConfig.language();
        DatasetCatalogConfig.DatasetDefinition datasetDefinition = null;
        if (catalog != null) {
            Map<String, DatasetCatalogConfig.DatasetDefinition> languageDatasets = catalog.get(language);
            if (languageDatasets != null) {
                datasetDefinition = languageDatasets.get(dataset);
            }
        }

        String sourceRepoUrl = datasetDefinition != null && StringUtils.hasText(datasetDefinition.sourceRepoUrl())
            ? datasetDefinition.sourceRepoUrl()
            : fallbackSourceRepoUrl(language, dataset);
        String defaultBranch = datasetDefinition != null && StringUtils.hasText(datasetDefinition.defaultBranch())
            ? datasetDefinition.defaultBranch()
            : "main";
        int expectedFindings = datasetDefinition != null && datasetDefinition.expectedFindings() != null && datasetDefinition.expectedFindings() > 0
            ? datasetDefinition.expectedFindings()
            : deriveExpectedFindings(language, dataset, sourceRepoUrl);

        String version = "simulated-" + defaultBranch + "-v" + (Math.abs(Objects.hash(language, dataset, sourceRepoUrl)) % 12 + 1);
        return new ExpectedDatasetBaseline(language, dataset, sourceRepoUrl, defaultBranch, expectedFindings, version);
    }

    private String fallbackSourceRepoUrl(String language, String dataset) {
        return "https://github.com/example-security/" + normalize(language) + "-" + normalize(dataset) + ".git";
    }

    private int deriveExpectedFindings(String language, String dataset, String sourceRepoUrl) {
        int seed = Math.abs(Objects.hash(language, dataset, sourceRepoUrl));
        return 28 + (seed % 67);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
