package com.devashish.learning.benchmarking.serivces;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Qualifier;

import com.devashish.learning.benchmarking.DTOs.BenchRunSubmitRequest;
import com.devashish.learning.benchmarking.configs.BenchmarkExecutionConfig;
import com.devashish.learning.benchmarking.configs.ExecutionMatrixConfig;
import com.devashish.learning.benchmarking.models.BenchmarkRunResponse;
import com.devashish.learning.benchmarking.models.JobStatus;
import com.devashish.learning.benchmarking.models.JobStatusResponse;
import com.devashish.learning.benchmarking.models.JobSubmitResponse;
import com.devashish.learning.benchmarking.models.Task;
import com.devashish.learning.benchmarking.models.TaskProgressView;
import com.devashish.learning.benchmarking.models.TaskStatus;
import com.devashish.learning.benchmarking.models.ToolExecutionResult;

@Service
public class BenchmarkJobService {

    private final ExecutionMatrixConfig executionConfig;
    private final RunOrchrestratorService runOrchrestratorService;
    private final BenchmarkExecutionConfig benchmarkExecutionConfig;
    private final Executor jobOrchestratorExecutor;
    private final Semaphore concurrencyLimiter;

    private final Map<UUID, JobRunState> jobs = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerState> circuitBreakerByTool = new ConcurrentHashMap<>();

    public BenchmarkJobService(
        ExecutionMatrixConfig executionConfig,
        RunOrchrestratorService runOrchrestratorService,
        BenchmarkExecutionConfig benchmarkExecutionConfig,
        @Qualifier("jobOrchestratorExecutor") Executor jobOrchestratorExecutor
    ) {
        this.executionConfig = executionConfig;
        this.runOrchrestratorService = runOrchrestratorService;
        this.benchmarkExecutionConfig = benchmarkExecutionConfig;
        this.jobOrchestratorExecutor = jobOrchestratorExecutor;
        this.concurrencyLimiter = new Semaphore(Math.max(1, benchmarkExecutionConfig.getMaxConcurrency()));
    }

    public JobSubmitResponse submitRun(BenchRunSubmitRequest request) {
        String requestedLanguage = request.language().trim();
        UUID runId = request.runid() == null ? UUID.randomUUID() : request.runid();

        List<TaskRunState> plannedTasks = buildTaskPlan(requestedLanguage);
        if (plannedTasks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tasks found for language: " + requestedLanguage);
        }

        JobRunState job = new JobRunState(runId, requestedLanguage, plannedTasks);
        if (jobs.putIfAbsent(runId, job) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run already exists for runId: " + runId);
        }

        CompletableFuture.runAsync(() -> processJob(job), jobOrchestratorExecutor);
        return new JobSubmitResponse(runId, job.status, plannedTasks.size(), job.submittedAt);
    }

    public JobStatusResponse getRunStatus(UUID runId) {
        JobRunState job = getJob(runId);
        return job.toStatusResponse();
    }

    public BenchmarkRunResponse getRunResults(UUID runId) {
        JobRunState job = getJob(runId);
        Map<String, Map<String, Map<String, String>>> nestedResults = new LinkedHashMap<>();

        for (TaskRunState task : job.tasks) {
            if (task.status == TaskStatus.SUCCESS && task.result != null) {
                nestedResults
                    .computeIfAbsent(task.task.language(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(task.task.dataset(), key -> new LinkedHashMap<>())
                    .put(task.task.tool(), task.result);
            }
        }
        return new BenchmarkRunResponse(nestedResults);
    }

    private JobRunState getJob(UUID runId) {
        JobRunState job = jobs.get(runId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found for runId: " + runId);
        }
        return job;
    }

    private void processJob(JobRunState job) {
        job.markRunning();
        List<CompletableFuture<Void>> futures = job.tasks.stream()
            .map(task -> CompletableFuture.runAsync(() -> executeTaskWithPolicies(task), jobOrchestratorExecutor))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        job.markCompleted();
    }

    private void executeTaskWithPolicies(TaskRunState taskState) {
        Task task = taskState.task;
        String toolName = task.tool();
        CircuitBreakerState circuitBreaker = circuitBreakerByTool.computeIfAbsent(toolName, key -> new CircuitBreakerState());

        if (circuitBreaker.isOpen()) {
            taskState.markTerminal(
                TaskStatus.SKIPPED_CIRCUIT_OPEN,
                "Skipped because circuit breaker is open for tool: " + toolName
            );
            return;
        }

        int maxAttempts = Math.max(1, benchmarkExecutionConfig.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            CompletableFuture<ToolExecutionResult> executionFuture = null;
            boolean permitAcquired = false;

            try {
                concurrencyLimiter.acquire();
                permitAcquired = true;

                taskState.markRunning(attempt);
                executionFuture = runOrchrestratorService.runTaskWithAppropriateTool(task);
                ToolExecutionResult toolExecutionResult = executionFuture.get(
                    benchmarkExecutionConfig.getTaskTimeoutMs(),
                    TimeUnit.MILLISECONDS
                );

                taskState.markSuccess(toolExecutionResult.result());
                circuitBreaker.onSuccess();
                return;
            } catch (TimeoutException ex) {
                if (executionFuture != null) {
                    executionFuture.cancel(true);
                }
                circuitBreaker.onFailure(benchmarkExecutionConfig);
                if (attempt == maxAttempts) {
                    taskState.markTerminal(TaskStatus.TIMEOUT, "Execution timed out after " + benchmarkExecutionConfig.getTaskTimeoutMs() + "ms");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                taskState.markTerminal(TaskStatus.FAILED, "Execution interrupted");
                return;
            } catch (ExecutionException ex) {
                circuitBreaker.onFailure(benchmarkExecutionConfig);
                if (attempt == maxAttempts) {
                    taskState.markTerminal(TaskStatus.FAILED, rootMessage(ex));
                }
            } catch (Exception ex) {
                circuitBreaker.onFailure(benchmarkExecutionConfig);
                if (attempt == maxAttempts) {
                    taskState.markTerminal(TaskStatus.FAILED, ex.getMessage());
                }
            } finally {
                if (permitAcquired) {
                    concurrencyLimiter.release();
                }
            }

            if (circuitBreaker.isOpen()) {
                taskState.markTerminal(
                    TaskStatus.SKIPPED_CIRCUIT_OPEN,
                    "Circuit breaker opened after repeated failures for tool: " + toolName
                );
                return;
            }
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Unknown error" : current.getMessage();
    }

    private List<TaskRunState> buildTaskPlan(String requestedLanguage) {
        List<TaskRunState> tasks = new ArrayList<>();
        int index = 1;

        for (Map.Entry<String, HashMap<String, ArrayList<String>>> languageEntry : executionConfig.language().entrySet()) {
            if (requestedLanguage.equalsIgnoreCase(languageEntry.getKey()) || "all".equalsIgnoreCase(requestedLanguage)) {
                for (Map.Entry<String, ArrayList<String>> datasetEntry : languageEntry.getValue().entrySet()) {
                    for (String tool : datasetEntry.getValue()) {
                        Task task = new Task(languageEntry.getKey(), datasetEntry.getKey(), tool);
                        tasks.add(new TaskRunState("task-" + index, task));
                        index++;
                    }
                }
            }
        }
        return tasks;
    }

    private static class CircuitBreakerState {
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private volatile Instant openUntil = Instant.EPOCH;

        boolean isOpen() {
            return Instant.now().isBefore(openUntil);
        }

        void onSuccess() {
            consecutiveFailures.set(0);
            openUntil = Instant.EPOCH;
        }

        void onFailure(BenchmarkExecutionConfig config) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= Math.max(1, config.getCircuitBreakerFailureThreshold())) {
                openUntil = Instant.now().plusMillis(Math.max(1, config.getCircuitBreakerOpenMs()));
                consecutiveFailures.set(0);
            }
        }
    }

    private static class TaskRunState {
        private final String taskId;
        private final Task task;

        private volatile TaskStatus status = TaskStatus.PENDING;
        private volatile int attempts = 0;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile String error;
        private volatile String result;

        TaskRunState(String taskId, Task task) {
            this.taskId = taskId;
            this.task = task;
        }

        synchronized void markRunning(int attempt) {
            this.attempts = attempt;
            this.status = TaskStatus.RUNNING;
            if (this.startedAt == null) {
                this.startedAt = Instant.now();
            }
            this.error = null;
        }

        synchronized void markSuccess(String result) {
            this.status = TaskStatus.SUCCESS;
            this.result = result;
            this.completedAt = Instant.now();
            this.error = null;
        }

        synchronized void markTerminal(TaskStatus status, String error) {
            this.status = status;
            this.error = error;
            this.completedAt = Instant.now();
        }

        TaskProgressView toView() {
            return new TaskProgressView(
                taskId,
                task.language(),
                task.dataset(),
                task.tool(),
                status,
                attempts,
                startedAt,
                completedAt,
                error
            );
        }
    }

    private static class JobRunState {
        private final UUID runId;
        private final String requestedLanguage;
        private final List<TaskRunState> tasks;
        private final Instant submittedAt;

        private volatile JobStatus status = JobStatus.QUEUED;
        private volatile Instant startedAt;
        private volatile Instant completedAt;

        JobRunState(UUID runId, String requestedLanguage, List<TaskRunState> tasks) {
            this.runId = runId;
            this.requestedLanguage = requestedLanguage;
            this.tasks = tasks;
            this.submittedAt = Instant.now();
        }

        synchronized void markRunning() {
            status = JobStatus.RUNNING;
            startedAt = Instant.now();
        }

        synchronized void markCompleted() {
            completedAt = Instant.now();

            long success = tasks.stream().filter(task -> task.status == TaskStatus.SUCCESS).count();
            long terminalFailures = tasks.stream()
                .filter(task -> task.status == TaskStatus.FAILED || task.status == TaskStatus.TIMEOUT || task.status == TaskStatus.SKIPPED_CIRCUIT_OPEN)
                .count();

            if (success == tasks.size()) {
                status = JobStatus.COMPLETED;
            } else if (success > 0) {
                status = JobStatus.PARTIALLY_COMPLETED;
            } else if (terminalFailures == tasks.size()) {
                status = JobStatus.FAILED;
            } else {
                status = JobStatus.PARTIALLY_COMPLETED;
            }
        }

        JobStatusResponse toStatusResponse() {
            int pending = 0;
            int running = 0;
            int success = 0;
            int failed = 0;
            int timeout = 0;
            int skipped = 0;

            for (TaskRunState task : tasks) {
                switch (task.status) {
                    case PENDING -> pending++;
                    case RUNNING -> running++;
                    case SUCCESS -> success++;
                    case FAILED -> failed++;
                    case TIMEOUT -> timeout++;
                    case SKIPPED_CIRCUIT_OPEN -> skipped++;
                }
            }

            List<TaskProgressView> taskViews = tasks.stream()
                .map(TaskRunState::toView)
                .sorted(Comparator.comparing(TaskProgressView::taskId))
                .toList();

            return new JobStatusResponse(
                runId,
                requestedLanguage,
                status,
                tasks.size(),
                pending,
                running,
                success,
                failed,
                timeout,
                skipped,
                submittedAt,
                startedAt,
                completedAt,
                taskViews
            );
        }
    }
}
