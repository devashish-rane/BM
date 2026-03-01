package com.devashish.learning.benchmarking.serivces;


import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.devashish.learning.benchmarking.interfaces.ITool;
import com.devashish.learning.benchmarking.models.Task;
import com.devashish.learning.benchmarking.models.ToolExecutionResult;

@Service
public class RunOrchrestratorService {

    private final Map<String, ITool> toolFactory;

    public RunOrchrestratorService(Map<String, ITool> toolFactory){
        this.toolFactory = toolFactory;
    }

    @Async("toolExecutorPool")
    public CompletableFuture<ToolExecutionResult> runTaskWithAppropriateTool(Task task){
            String tool = task.tool();
            if(this.toolFactory.containsKey(tool)) {
                String result = this.toolFactory.get(tool).executeTool(task);
                return CompletableFuture.completedFuture(
                    new ToolExecutionResult(task.language(), task.dataset(), tool, result)
                );
            } else
                return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported tool: " + tool));
    }
}
