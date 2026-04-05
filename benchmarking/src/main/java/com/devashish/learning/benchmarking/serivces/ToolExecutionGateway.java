package com.devashish.learning.benchmarking.serivces;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.devashish.learning.benchmarking.interfaces.ITool;
import com.devashish.learning.benchmarking.models.ToolExecutionRequest;
import com.devashish.learning.benchmarking.models.ToolExecutionResult;

@Service
public class ToolExecutionGateway {

    private final Map<String, ITool> toolFactory;

    public ToolExecutionGateway(Map<String, ITool> toolFactory) {
        this.toolFactory = toolFactory;
    }

    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ITool tool = toolFactory.get(request.tool());
        if (tool == null) {
            throw new IllegalArgumentException("Unsupported tool: " + request.tool());
        }
        String result = tool.executeTool(request);
        return new ToolExecutionResult(request.language(), request.dataset(), request.tool(), result);
    }
}
