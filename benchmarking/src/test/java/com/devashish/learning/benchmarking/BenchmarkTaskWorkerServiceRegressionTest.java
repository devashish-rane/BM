package com.devashish.learning.benchmarking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.devashish.learning.benchmarking.DTOs.BenchRunSubmitRequest;
import com.devashish.learning.benchmarking.models.JobStatus;
import com.devashish.learning.benchmarking.models.JobStatusResponse;
import com.devashish.learning.benchmarking.models.JobSubmitResponse;
import com.devashish.learning.benchmarking.models.ToolExecutionRequest;
import com.devashish.learning.benchmarking.serivces.RunOrchrestratorService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:configs/execution-matrix-test.yaml",
    "benchmark.execution.max-concurrency=2",
    "benchmark.execution.max-retries=0",
    "benchmark.execution.task-timeout-ms=5000",
    "benchmark.execution.stale-running-task-grace-ms=600000",
    "benchmark.execution.worker-batch-size=2",
    "benchmark.execution.worker-poll-interval-ms=100",
    "benchmark.execution.retry-backoff-ms=200"
})
class BenchmarkTaskWorkerServiceRegressionTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("benchmarking")
        .withUsername("benchmarking")
        .withPassword("benchmarking");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @MockBean
    private RunOrchrestratorService runOrchrestratorService;

    @Test
    void synchronousOrchestratorFailureDoesNotLeaveTasksRunning() throws Exception {
        when(runOrchrestratorService.runTaskWithAppropriateTool(any(ToolExecutionRequest.class)))
            .thenThrow(new IllegalStateException("synthetic orchestrator failure"));

        UUID runId = UUID.randomUUID();
        ResponseEntity<JobSubmitResponse> submitResponse = restTemplate.postForEntity(
            url("/bench/runs"),
            new BenchRunSubmitRequest(runId, "java"),
            JobSubmitResponse.class
        );

        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JobStatusResponse statusResponse = waitForTerminalStatus(runId, Duration.ofSeconds(10));

        assertThat(statusResponse.status()).isEqualTo(JobStatus.FAILED);
        assertThat(statusResponse.runningTasks()).isZero();
        assertThat(statusResponse.pendingTasks()).isZero();
        assertThat(statusResponse.failedTasks() + statusResponse.timeoutTasks() + statusResponse.skippedTasks() + statusResponse.cancelledTasks())
            .isEqualTo(statusResponse.totalTasks());
    }

    private JobStatusResponse waitForTerminalStatus(UUID runId, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        ResponseEntity<JobStatusResponse> response = null;

        while (System.currentTimeMillis() < deadline) {
            response = restTemplate.getForEntity(url("/bench/runs/" + runId), JobStatusResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            if (response.getBody().status() == JobStatus.COMPLETED
                || response.getBody().status() == JobStatus.PARTIALLY_COMPLETED
                || response.getBody().status() == JobStatus.FAILED
                || response.getBody().status() == JobStatus.CANCELLED) {
                return response.getBody();
            }
            Thread.sleep(100);
        }

        assertThat(response).as("Expected run to reach terminal status within %s", timeout).isNotNull();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
