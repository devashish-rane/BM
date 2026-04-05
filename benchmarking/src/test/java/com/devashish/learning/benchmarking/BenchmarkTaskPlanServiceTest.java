package com.devashish.learning.benchmarking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.devashish.learning.benchmarking.configs.ExecutionMatrixConfig;
import com.devashish.learning.benchmarking.models.ToolExecutionRequest;
import com.devashish.learning.benchmarking.serivces.BenchmarkTaskPlanService;

class BenchmarkTaskPlanServiceTest {

    @Test
    void planTasksReturnsAllConfiguredTasksForRequestedLanguage() {
        HashMap<String, ArrayList<String>> javaDatasets = new HashMap<>();
        javaDatasets.put("owasp", new ArrayList<>(List.of("semgrep", "qca")));

        HashMap<String, HashMap<String, ArrayList<String>>> configMap = new HashMap<>();
        configMap.put("java", javaDatasets);
        configMap.put("python", new HashMap<>());

        BenchmarkTaskPlanService service = new BenchmarkTaskPlanService(new ExecutionMatrixConfig(configMap));

        List<ToolExecutionRequest> plannedTasks = service.planTasks("java");

        assertThat(plannedTasks)
            .extracting(ToolExecutionRequest::language, ToolExecutionRequest::dataset, ToolExecutionRequest::tool)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("java", "owasp", "semgrep"),
                org.assertj.core.groups.Tuple.tuple("java", "owasp", "qca")
            );
    }
}
