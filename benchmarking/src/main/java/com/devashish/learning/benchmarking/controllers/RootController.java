package com.devashish.learning.benchmarking.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devashish.learning.benchmarking.DTOs.BenchRunSubmitRequest;
import com.devashish.learning.benchmarking.DTOs.BenchScanRequest;
import com.devashish.learning.benchmarking.DTOs.ResumeRunRequest;
import com.devashish.learning.benchmarking.models.BenchmarkRunResponse;
import com.devashish.learning.benchmarking.models.JobStatus;
import com.devashish.learning.benchmarking.models.JobStatusResponse;
import com.devashish.learning.benchmarking.models.JobSubmitResponse;
import com.devashish.learning.benchmarking.models.PagedBenchmarkRunsResponse;
import com.devashish.learning.benchmarking.models.TaskProgressView;
import com.devashish.learning.benchmarking.serivces.BenchmarkRunCommandService;
import com.devashish.learning.benchmarking.serivces.BenchmarkRunQueryService;
import com.devashish.learning.benchmarking.serivces.BenchmarkingService;
import com.devashish.learning.benchmarking.models.evaluation.BenchmarkEvaluationResponse;
import com.devashish.learning.benchmarking.serivces.evaluation.BenchmarkEvaluationService;
import com.devashish.learning.benchmarking.serivces.evaluation.BenchmarkTrendChartService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/bench")
public class RootController {

    private final BenchmarkingService bmService;
    private final BenchmarkRunCommandService benchmarkRunCommandService;
    private final BenchmarkRunQueryService benchmarkRunQueryService;
    private final BenchmarkEvaluationService benchmarkEvaluationService;
    private final BenchmarkTrendChartService benchmarkTrendChartService;

    public RootController(
        BenchmarkingService bmService,
        BenchmarkRunCommandService benchmarkRunCommandService,
        BenchmarkRunQueryService benchmarkRunQueryService,
        BenchmarkEvaluationService benchmarkEvaluationService,
        BenchmarkTrendChartService benchmarkTrendChartService
    ){
        this.bmService = bmService;
        this.benchmarkRunCommandService = benchmarkRunCommandService;
        this.benchmarkRunQueryService = benchmarkRunQueryService;
        this.benchmarkEvaluationService = benchmarkEvaluationService;
        this.benchmarkTrendChartService = benchmarkTrendChartService;
    }

    @PostMapping("/weekly-trigger")
    public ResponseEntity<BenchmarkRunResponse> triggerFullWeeklyBM( @Valid @RequestBody BenchScanRequest benchScanRequest){
        BenchmarkRunResponse response = this.bmService.triggerFullWeeklyBM(benchScanRequest);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/runs")
    public ResponseEntity<JobSubmitResponse> submitRun(
        @Valid @RequestBody BenchRunSubmitRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        JobSubmitResponse response = benchmarkRunCommandService.submitRun(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<JobStatusResponse> getRunStatus(@PathVariable UUID runId) {
        JobStatusResponse response = benchmarkRunQueryService.getRunStatus(runId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/runs/{runId}/results")
    public ResponseEntity<BenchmarkRunResponse> getRunResults(@PathVariable UUID runId) {
        BenchmarkRunResponse response = benchmarkRunQueryService.getRunResults(runId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/runs/{runId}/evaluations")
    public ResponseEntity<BenchmarkEvaluationResponse> getRunEvaluations(@PathVariable UUID runId) {
        return ResponseEntity.ok(benchmarkEvaluationService.getRunEvaluations(runId));
    }

    @GetMapping("/analytics/overview")
    public ResponseEntity<BenchmarkEvaluationResponse> getAnalyticsOverview() {
        return ResponseEntity.ok(benchmarkEvaluationService.getAnalyticsOverview());
    }

    @GetMapping("/runs/{runId}/tasks")
    public ResponseEntity<java.util.List<TaskProgressView>> getRunTasks(@PathVariable UUID runId) {
        return ResponseEntity.ok(benchmarkRunQueryService.getRunTasks(runId));
    }

    @GetMapping("/runs")
    public ResponseEntity<PagedBenchmarkRunsResponse> listRuns(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String language,
        @RequestParam(required = false) JobStatus status
    ) {
        return ResponseEntity.ok(benchmarkRunQueryService.listRuns(page, size, language, status));
    }

    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<JobStatusResponse> cancelRun(@PathVariable UUID runId) {
        return ResponseEntity.ok(benchmarkRunCommandService.cancelRun(runId));
    }

    @PostMapping("/runs/{runId}/resume")
    public ResponseEntity<JobStatusResponse> resumeRun(@PathVariable UUID runId, @RequestBody(required = false) ResumeRunRequest request) {
        return ResponseEntity.ok(benchmarkRunCommandService.resumeRun(runId, request));
    }

    @PostMapping("/runs/{runId}/archive")
    public ResponseEntity<JobStatusResponse> archiveRun(@PathVariable UUID runId) {
        return ResponseEntity.ok(benchmarkRunCommandService.archiveRun(runId));
    }

    @GetMapping(value = "/charts/week-over-week", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewWeekOverWeekChart(
        @RequestParam("runId") UUID runId,
        @RequestParam("language") String language,
        @RequestParam("dataset") String dataset,
        @RequestParam("tool") String tool
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(benchmarkTrendChartService.renderWeekOverWeekChart(runId, language, dataset, tool));
    }

    @GetMapping(value = "/charts/week-over-week/{runId}/{language}/{dataset}/{tool}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewWeekOverWeekChartByPath(
        @PathVariable UUID runId,
        @PathVariable String language,
        @PathVariable String dataset,
        @PathVariable String tool
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(benchmarkTrendChartService.renderWeekOverWeekChart(runId, language, dataset, tool));
    }
}
