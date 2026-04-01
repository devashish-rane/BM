package com.devashish.learning.benchmarking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.devashish.learning.benchmarking.DTOs.BenchRunSubmitRequest;
import com.devashish.learning.benchmarking.models.BenchmarkRunResponse;
import com.devashish.learning.benchmarking.models.JobStatus;
import com.devashish.learning.benchmarking.models.JobStatusResponse;
import com.devashish.learning.benchmarking.models.JobSubmitResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:configs/execution-matrix-test.yaml",
    "benchmark.execution.max-concurrency=2",
    "benchmark.execution.max-retries=0",
    "benchmark.execution.task-timeout-ms=10000",
    "benchmark.execution.worker-batch-size=2",
    "benchmark.execution.worker-poll-interval-ms=200",
    "benchmark.execution.retry-backoff-ms=200"
})
class BenchmarkingApplicationTests {

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

    @Test
    void submitRunProcessesTasksAndPersistsResults() throws Exception {
        UUID runId = UUID.randomUUID();
        ResponseEntity<JobSubmitResponse> submitResponse = restTemplate.postForEntity(
            url("/bench/runs"),
            new BenchRunSubmitRequest(runId, "java"),
            JobSubmitResponse.class
        );

        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(submitResponse.getBody()).isNotNull();
        assertThat(submitResponse.getBody().runId()).isEqualTo(runId);
        assertThat(submitResponse.getBody().totalTasks()).isEqualTo(2);

        JobStatusResponse statusResponse = waitForTerminalStatus(runId, Duration.ofSeconds(20));
        assertThat(statusResponse.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(statusResponse.successTasks()).isEqualTo(2);
        assertThat(statusResponse.pendingTasks()).isZero();
        assertThat(statusResponse.runningTasks()).isZero();

        ResponseEntity<BenchmarkRunResponse> resultsResponse = restTemplate.getForEntity(
            url("/bench/runs/" + runId + "/results"),
            BenchmarkRunResponse.class
        );

        assertThat(resultsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultsResponse.getBody()).isNotNull();
        assertThat(resultsResponse.getBody().results()).containsKey("java");
        assertThat(resultsResponse.getBody().results().get("java")).containsKey("smoke");
        assertThat(resultsResponse.getBody().results().get("java").get("smoke")).containsKeys("semgrep", "qca");
    }

    @Test
    void idempotencyKeyReturnsExistingRunInsteadOfCreatingAnother() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "run-java-smoke-1");

        ResponseEntity<JobSubmitResponse> first = restTemplate.exchange(
            url("/bench/runs"),
            HttpMethod.POST,
            new HttpEntity<>(new BenchRunSubmitRequest(UUID.randomUUID(), "java"), headers),
            JobSubmitResponse.class
        );
        ResponseEntity<JobSubmitResponse> second = restTemplate.exchange(
            url("/bench/runs"),
            HttpMethod.POST,
            new HttpEntity<>(new BenchRunSubmitRequest(UUID.randomUUID(), "java"), headers),
            JobSubmitResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().runId()).isEqualTo(first.getBody().runId());
        assertThat(second.getBody().totalTasks()).isEqualTo(first.getBody().totalTasks());
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
            Thread.sleep(250);
        }

        assertThat(response).as("Expected run to reach terminal status within %s", timeout).isNotNull();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
