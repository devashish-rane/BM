package com.devashish.learning.benchmarking.serivces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.devashish.learning.benchmarking.configs.ExecutionMatrixConfig;
import com.devashish.learning.benchmarking.models.ToolExecutionRequest;

@Service
public class BenchmarkTaskPlanService {

    private final ExecutionMatrixConfig executionMatrixConfig;

    public BenchmarkTaskPlanService(ExecutionMatrixConfig executionMatrixConfig) {
        this.executionMatrixConfig = executionMatrixConfig;
    }

    public List<ToolExecutionRequest> planTasks(String requestedLanguage) {
        List<ToolExecutionRequest> tasks = new ArrayList<>();
        for (Map.Entry<String, HashMap<String, ArrayList<String>>> languageEntry : executionMatrixConfig.language().entrySet()) {
            if (requestedLanguage.equalsIgnoreCase(languageEntry.getKey()) || "all".equalsIgnoreCase(requestedLanguage)) {
                for (Map.Entry<String, ArrayList<String>> datasetEntry : languageEntry.getValue().entrySet()) {
                    for (String tool : datasetEntry.getValue()) {
                        tasks.add(new ToolExecutionRequest(languageEntry.getKey(), datasetEntry.getKey(), tool));
                    }
                }
            }
        }
        return tasks;
    }
}
