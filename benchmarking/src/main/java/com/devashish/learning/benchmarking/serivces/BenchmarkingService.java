package com.devashish.learning.benchmarking.serivces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.devashish.learning.benchmarking.DTOs.BenchScanRequest;
import com.devashish.learning.benchmarking.configs.ExecutionMatrixConfig;
import com.devashish.learning.benchmarking.models.BenchmarkRunResponse;
import com.devashish.learning.benchmarking.models.Task;
import com.devashish.learning.benchmarking.models.ToolExecutionResult;

@Component
public class BenchmarkingService {

    ExecutionMatrixConfig executionConfig;
    RunOrchrestratorService runOrchrestratorService;
    
    public BenchmarkingService(ExecutionMatrixConfig executionConfig, RunOrchrestratorService runOrchrestratorService){
        this.executionConfig=  executionConfig;
        this.runOrchrestratorService = runOrchrestratorService;
    }

    public BenchmarkRunResponse triggerFullWeeklyBM(BenchScanRequest benchScanRequest){ 
        ArrayList<CompletableFuture<ToolExecutionResult>> futures = new ArrayList<>();
        String language = benchScanRequest.language();
        if (language == null || language.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "language must not be blank");
        }
        String requestedLanguage = language.trim();

        
        for( Map.Entry<String, HashMap<String,ArrayList<String>>>  languageEntry : executionConfig.language().entrySet()){
            // go == matches with go somehwre OR 
            if(requestedLanguage.equalsIgnoreCase(languageEntry.getKey()) || "all".equalsIgnoreCase(requestedLanguage))
                for( Map.Entry<String, ArrayList<String>>  dataset : languageEntry.getValue().entrySet()){
                    for( String  tool : dataset.getValue()){
                        futures.add(runOrchrestratorService.runTaskWithAppropriateTool(new Task(languageEntry.getKey(), dataset.getKey(),tool)));
                        
                    } 
                }
        }
        // can be skipped as we are doing same in for below
        // but current code is also fine
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
