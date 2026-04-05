package com.devashish.learning.benchmarking.serivces;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.devashish.learning.benchmarking.models.ToolExecutionRequest;
import com.devashish.learning.benchmarking.models.ToolExecutionResult;

@Service
public class RunOrchrestratorService {

    private final ToolExecutionGateway toolExecutionGateway;

    public RunOrchrestratorService(ToolExecutionGateway toolExecutionGateway){
        this.toolExecutionGateway = toolExecutionGateway;
    }

    @Async("toolExecutorPool")
    public CompletableFuture<ToolExecutionResult> runTaskWithAppropriateTool(ToolExecutionRequest task){
        return CompletableFuture.completedFuture(toolExecutionGateway.execute(task));
    }
}
