package com.devashish.learning.benchmarking.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devashish.learning.benchmarking.DTOs.BenchRunSubmitRequest;
import com.devashish.learning.benchmarking.DTOs.BenchScanRequest;
import com.devashish.learning.benchmarking.models.BenchmarkRunResponse;
import com.devashish.learning.benchmarking.models.JobStatusResponse;
import com.devashish.learning.benchmarking.models.JobSubmitResponse;
import com.devashish.learning.benchmarking.serivces.BenchmarkJobService;
import com.devashish.learning.benchmarking.serivces.BenchmarkingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/bench")
public class RootController {

    private final BenchmarkingService bmService;
    private final BenchmarkJobService benchmarkJobService;

    public RootController(BenchmarkingService bmService, BenchmarkJobService benchmarkJobService){
        this.bmService = bmService;
        this.benchmarkJobService = benchmarkJobService;
    }

    @PostMapping("/weekly-trigger")
    public ResponseEntity<BenchmarkRunResponse> triggerFullWeeklyBM( @Valid @RequestBody BenchScanRequest benchScanRequest){
        BenchmarkRunResponse response = this.bmService.triggerFullWeeklyBM(benchScanRequest);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/runs")
    public ResponseEntity<JobSubmitResponse> submitRun(@Valid @RequestBody BenchRunSubmitRequest request) {
        JobSubmitResponse response = benchmarkJobService.submitRun(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<JobStatusResponse> getRunStatus(@PathVariable UUID runId) {
        JobStatusResponse response = benchmarkJobService.getRunStatus(runId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/runs/{runId}/results")
    public ResponseEntity<BenchmarkRunResponse> getRunResults(@PathVariable UUID runId) {
        BenchmarkRunResponse response = benchmarkJobService.getRunResults(runId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
