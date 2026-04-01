package com.devashish.learning.benchmarking.serivces.evaluation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.devashish.learning.benchmarking.exceptions.ResourceNotFoundException;
import com.devashish.learning.benchmarking.models.TaskStatus;
import com.devashish.learning.benchmarking.models.evaluation.BenchmarkEvaluationResponse;
import com.devashish.learning.benchmarking.models.evaluation.DatasetEvaluationResponse;
import com.devashish.learning.benchmarking.models.evaluation.ExpectedDatasetBaseline;
import com.devashish.learning.benchmarking.models.evaluation.ToolEvaluationResponse;
import com.devashish.learning.benchmarking.persistence.entities.BenchmarkRunEntity;
import com.devashish.learning.benchmarking.persistence.entities.BenchmarkTaskEntity;
import com.devashish.learning.benchmarking.repositories.BenchmarkRunRepository;
import com.devashish.learning.benchmarking.repositories.BenchmarkTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class BenchmarkEvaluationService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkTaskRepository benchmarkTaskRepository;
    private final ExpectedResultGeneratorService expectedResultGeneratorService;
    private final ObjectMapper objectMapper;

    public BenchmarkEvaluationService(
        BenchmarkRunRepository benchmarkRunRepository,
        BenchmarkTaskRepository benchmarkTaskRepository,
        ExpectedResultGeneratorService expectedResultGeneratorService,
        ObjectMapper objectMapper
    ) {
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.benchmarkTaskRepository = benchmarkTaskRepository;
        this.expectedResultGeneratorService = expectedResultGeneratorService;
        this.objectMapper = objectMapper;
    }

    public BenchmarkEvaluationResponse getRunEvaluations(UUID runId) {
        BenchmarkRunEntity run = benchmarkRunRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("Run not found for runId: " + runId));
        List<BenchmarkTaskEntity> tasks = benchmarkTaskRepository.findByRunIdOrderByLanguageAscDatasetAscToolAsc(runId);
        return new BenchmarkEvaluationResponse(buildEvaluations(run, tasks));
    }

    public ToolEvaluationResponse getToolEvaluation(UUID runId, String language, String dataset, String tool) {
        BenchmarkRunEntity run = benchmarkRunRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("Run not found for runId: " + runId));
        BenchmarkTaskEntity task = benchmarkTaskRepository.findByRunIdOrderByLanguageAscDatasetAscToolAsc(runId)
            .stream()
            .filter(candidate -> candidate.getLanguage().equals(language))
            .filter(candidate -> candidate.getDataset().equals(dataset))
            .filter(candidate -> candidate.getTool().equals(tool))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(
                "Tool evaluation not found for runId=" + runId + ", language=" + language + ", dataset=" + dataset + ", tool=" + tool
            ));
        return evaluateTool(run, task, expectedResultGeneratorService.generate(language, dataset));
    }

    public ToolEvaluationResponse evaluateHistoricalTask(BenchmarkRunEntity run, BenchmarkTaskEntity task) {
        return evaluateTool(run, task, expectedResultGeneratorService.generate(task.getLanguage(), task.getDataset()));
    }

    private Map<String, Map<String, DatasetEvaluationResponse>> buildEvaluations(BenchmarkRunEntity run, List<BenchmarkTaskEntity> tasks) {
        Map<String, Map<String, DatasetEvaluationResponse>> evaluations = new LinkedHashMap<>();
        Map<String, List<BenchmarkTaskEntity>> groupedByDataset = tasks.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                task -> task.getLanguage() + "||" + task.getDataset(),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));

        for (List<BenchmarkTaskEntity> datasetTasks : groupedByDataset.values()) {
            if (datasetTasks.isEmpty()) {
                continue;
            }

            BenchmarkTaskEntity firstTask = datasetTasks.get(0);
            ExpectedDatasetBaseline baseline = expectedResultGeneratorService.generate(firstTask.getLanguage(), firstTask.getDataset());
            Map<String, ToolEvaluationResponse> toolEvaluations = new LinkedHashMap<>();

            for (BenchmarkTaskEntity task : datasetTasks) {
                ToolEvaluationResponse toolEvaluation = evaluateTool(run, task, baseline);
                toolEvaluations.put(task.getTool(), toolEvaluation);
            }

            evaluations
                .computeIfAbsent(firstTask.getLanguage(), key -> new LinkedHashMap<>())
                .put(firstTask.getDataset(), new DatasetEvaluationResponse(
                    baseline.sourceRepoUrl(),
                    baseline.defaultBranch(),
                    baseline.expectedFindings(),
                    baseline.expectedResultVersion(),
                    toolEvaluations
                ));
        }

        return evaluations;
    }

    private ToolEvaluationResponse evaluateTool(BenchmarkRunEntity run, BenchmarkTaskEntity task, ExpectedDatasetBaseline baseline) {
        int expectedFindings = baseline.expectedFindings();
        int detectedFindings = extractDetectionCount(task.getStatus(), task.getRawResult());
        int matchedFindings = simulateMatchedFindings(expectedFindings, detectedFindings, task.getLanguage(), task.getDataset(), task.getTool());
        double precision = detectedFindings == 0 ? 0.0 : round4((double) matchedFindings / detectedFindings);
        double recall = expectedFindings == 0 ? 1.0 : round4((double) matchedFindings / expectedFindings);
        double f1Score = precision + recall == 0.0 ? 0.0 : round4((2.0 * precision * recall) / (precision + recall));

        return new ToolEvaluationResponse(
            task.getTool(),
            expectedFindings,
            detectedFindings,
            matchedFindings,
            precision,
            recall,
            f1Score,
            baseline.sourceRepoUrl(),
            baseline.expectedResultVersion(),
            buildChartUrl(run.getId(), task.getLanguage(), task.getDataset(), task.getTool())
        );
    }

    private int extractDetectionCount(TaskStatus status, String rawResult) {
        if (status != TaskStatus.SUCCESS || rawResult == null || rawResult.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(rawResult);
            if (root.isArray() || root.isObject()) {
                return root.size();
            }
            return 1;
        } catch (Exception ex) {
            return (int) rawResult.lines().filter(line -> !line.isBlank()).count();
        }
    }

    private int simulateMatchedFindings(int expectedFindings, int detectedFindings, String language, String dataset, String tool) {
        if (expectedFindings == 0 || detectedFindings == 0) {
            return 0;
        }

        double toolBias = switch (tool.toLowerCase(Locale.ROOT)) {
            case "semgrep" -> 0.78;
            case "qca" -> 0.72;
            case "llm" -> 0.67;
            default -> 0.7;
        };
        double datasetBias = 0.72 + ((Math.abs(Objects.hash(language, dataset)) % 18) / 100.0);
        int proposedMatchCount = (int) Math.round(expectedFindings * toolBias * datasetBias);
        return Math.max(0, Math.min(Math.min(expectedFindings, detectedFindings), proposedMatchCount));
    }

    private String buildChartUrl(UUID runId, String language, String dataset, String tool) {
        return UriComponentsBuilder.fromPath("/bench/charts/week-over-week/{runId}/{language}/{dataset}/{tool}")
            .buildAndExpand(runId, language, dataset, tool)
            .toUriString();
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
