package com.devashish.learning.benchmarking.serivces;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;

import com.devashish.learning.benchmarking.DTOs.BenchScanRequest;
import com.devashish.learning.benchmarking.exceptions.BadRequestException;
import com.devashish.learning.benchmarking.models.BenchmarkRunResponse;
import com.devashish.learning.benchmarking.models.ToolExecutionResult;
import com.devashish.learning.benchmarking.models.ToolExecutionRequest;

@Component
public class BenchmarkingService {

    private final BenchmarkTaskPlanService benchmarkTaskPlanService;
    private final RunOrchrestratorService runOrchrestratorService;
    
    public BenchmarkingService(BenchmarkTaskPlanService benchmarkTaskPlanService, RunOrchrestratorService runOrchrestratorService){
        this.benchmarkTaskPlanService = benchmarkTaskPlanService;
        this.runOrchrestratorService = runOrchrestratorService;
    }

    public BenchmarkRunResponse triggerFullWeeklyBM(BenchScanRequest benchScanRequest){ 
        ArrayList<CompletableFuture<ToolExecutionResult>> futures = new ArrayList<>();
        String language = benchScanRequest.language();
        if (language == null || language.isBlank()) {
            throw new BadRequestException("language must not be blank");
        }
        String requestedLanguage = language.trim();

        var plannedTasks = benchmarkTaskPlanService.planTasks(requestedLanguage);
        if (plannedTasks.isEmpty()) {
            throw new BadRequestException("No tasks found for language: " + requestedLanguage);
        }

        for (ToolExecutionRequest task : plannedTasks) {
            futures.add(runOrchrestratorService.runTaskWithAppropriateTool(task));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Map<String, Map<String, Map<String, String>>> nestedResults = new LinkedHashMap<>();
        for (ToolExecutionResult executionResult : futures.stream().map(CompletableFuture::join).toList()) {
            nestedResults
                .computeIfAbsent(executionResult.language(), key -> new LinkedHashMap<>())
                .computeIfAbsent(executionResult.dataset(), key -> new LinkedHashMap<>())
                .put(executionResult.tool(), executionResult.result());
        }

        return new BenchmarkRunResponse(nestedResults);
    }
}
