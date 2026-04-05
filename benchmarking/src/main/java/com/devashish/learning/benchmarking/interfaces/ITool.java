package com.devashish.learning.benchmarking.interfaces;

import com.devashish.learning.benchmarking.models.ToolExecutionRequest;

public interface ITool {
    String executeTool(ToolExecutionRequest task);
}
