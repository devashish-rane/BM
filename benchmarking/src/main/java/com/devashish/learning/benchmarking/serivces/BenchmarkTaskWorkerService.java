package com.devashish.learning.benchmarking.serivces;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.devashish.learning.benchmarking.configs.BenchmarkExecutionConfig;
import com.devashish.learning.benchmarking.models.JobStatus;
import com.devashish.learning.benchmarking.models.TaskStatus;
import com.devashish.learning.benchmarking.models.ToolExecutionResult;
import com.devashish.learning.benchmarking.persistence.entities.BenchmarkRunEntity;
import com.devashish.learning.benchmarking.persistence.entities.BenchmarkTaskEntity;
import com.devashish.learning.benchmarking.persistence.entities.TaskAttemptEntity;
import com.devashish.learning.benchmarking.repositories.BenchmarkRunRepository;
import com.devashish.learning.benchmarking.repositories.BenchmarkTaskRepository;
import com.devashish.learning.benchmarking.repositories.TaskAttemptRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class BenchmarkTaskWorkerService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkTaskWorkerService.class);

    private final BenchmarkTaskRepository benchmarkTaskRepository;
    private final BenchmarkRunRepository benchmarkRunRepository;
    private final TaskAttemptRepository taskAttemptRepository;
    private final RunOrchrestratorService runOrchrestratorService;
    private final BenchmarkExecutionConfig benchmarkExecutionConfig;
    private final Executor taskWorkerExecutor;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final Semaphore concurrencyLimiter;
    private final Map<String, CircuitBreakerState> circuitBreakerByTool = new java.util.concurrent.ConcurrentHashMap<>();

    public BenchmarkTaskWorkerService(
        BenchmarkTaskRepository benchmarkTaskRepository,
        BenchmarkRunRepository benchmarkRunRepository,
        TaskAttemptRepository taskAttemptRepository,
        RunOrchrestratorService runOrchrestratorService,
        BenchmarkExecutionConfig benchmarkExecutionConfig,
        @Qualifier("taskWorkerExecutor") Executor taskWorkerExecutor,
        PlatformTransactionManager transactionManager,
        MeterRegistry meterRegistry
    ) {
        this.benchmarkTaskRepository = benchmarkTaskRepository;
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.taskAttemptRepository = taskAttemptRepository;
        this.runOrchrestratorService = runOrchrestratorService;
        this.benchmarkExecutionConfig = benchmarkExecutionConfig;
        this.taskWorkerExecutor = taskWorkerExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
        this.concurrencyLimiter = new Semaphore(Math.max(1, benchmarkExecutionConfig.getMaxConcurrency()));
    }

    @Scheduled(fixedDelayString = "${benchmark.execution.worker-poll-interval-ms:1000}")
    public void pollAndDispatch() {
        recoverStaleRunningTasks();

        int availableSlots = concurrencyLimiter.availablePermits();
        if (availableSlots <= 0) {
            return;
        }

        List<ClaimedTask> claimedTasks = claimReadyTasks(Math.min(availableSlots, Math.max(1, benchmarkExecutionConfig.getWorkerBatchSize())));
        if (!claimedTasks.isEmpty()) {
            log.info("Claimed {} benchmark task(s) for execution", claimedTasks.size());
        }
        for (ClaimedTask claimedTask : claimedTasks) {
            if (!concurrencyLimiter.tryAcquire()) {
                break;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    executeClaimedTask(claimedTask);
                } finally {
                    concurrencyLimiter.release();
                }
            }, taskWorkerExecutor);
        }
    }

    private List<ClaimedTask> claimReadyTasks(int limit) {
        List<ClaimedTask> claimedTasks = transactionTemplate.execute(status -> {
            List<ClaimedTask> claimedTaskBatch = new ArrayList<>();
            Instant now = Instant.now();
            int claimWindowSize = Math.max(limit, benchmarkExecutionConfig.getClaimWindowSize());
            List<BenchmarkTaskEntity> candidateTasks = benchmarkTaskRepository.findReadyTasksForClaim(claimWindowSize);
            List<BenchmarkTaskEntity> tasks = pickTasksFairly(candidateTasks, limit);
            for (BenchmarkTaskEntity task : tasks) {
                BenchmarkRunEntity run = task.getRun();
                if (run.getStatus() == JobStatus.CANCELLED) {
                    task.markCancelled(now, "Cancelled before execution");
                    run.markPendingTaskCancelled(now);
                    benchmarkTaskRepository.save(task);
                    benchmarkRunRepository.save(run);
                    continue;
                }

                task.markRunning(now);
                run.markTaskRunning(now);

                TaskAttemptEntity attempt = new TaskAttemptEntity(
                    UUID.randomUUID(),
                    task,
                    task.getAttemptCount(),
                    TaskStatus.RUNNING,
                    now
                );
                task.addAttempt(attempt);
                benchmarkTaskRepository.save(task);
                benchmarkRunRepository.save(run);
                taskAttemptRepository.save(attempt);

                claimedTaskBatch.add(new ClaimedTask(task.getId(), run.getId(), attempt.getId(), task.getAttemptCount(), task.getMaxAttempts(), task.getTool(), task.toExecutionRequest()));
            }
            return claimedTaskBatch;
        });
        return claimedTasks == null ? List.of() : claimedTasks;
    }

    private void recoverStaleRunningTasks() {
        Instant now = Instant.now();
        Instant cutoff = now.minusMillis(
            Math.max(1L, benchmarkExecutionConfig.getTaskTimeoutMs() + benchmarkExecutionConfig.getStaleRunningTaskGraceMs())
        );

        Integer recoveredCount = transactionTemplate.execute(status -> {
            List<BenchmarkTaskEntity> staleTasks = benchmarkTaskRepository.findStaleRunningTasks(cutoff);
            if (staleTasks.isEmpty()) {
                return 0;
            }

            Map<UUID, List<BenchmarkTaskEntity>> tasksByRun = new LinkedHashMap<>();
            for (BenchmarkTaskEntity task : staleTasks) {
                String errorMessage = "Recovered stale running task after exceeding timeout window";
                Instant nextRetryAt = now.plusMillis(Math.max(1L, benchmarkExecutionConfig.getRetryBackoffMs()));

                if (task.hasRemainingAttempts()) {
                    task.markRetryableFailure(TaskStatus.TIMEOUT, errorMessage, now, nextRetryAt);
                } else {
                    task.markTerminal(TaskStatus.TIMEOUT, errorMessage, null, now);
                }
                benchmarkTaskRepository.save(task);

                taskAttemptRepository.findTopByTaskIdOrderByAttemptNumberDesc(task.getId())
                    .ifPresent(attempt -> attempt.markCompleted(
                        TaskStatus.TIMEOUT,
                        errorMessage,
                        computeDurationMs(task.getStartedAt(), now),
                        now
                    ));

                tasksByRun.computeIfAbsent(task.getRun().getId(), key -> new ArrayList<>()).add(task);
            }

            for (UUID runId : tasksByRun.keySet()) {
                BenchmarkRunEntity run = benchmarkRunRepository.findById(runId).orElseThrow();
                List<BenchmarkTaskEntity> runTasks = benchmarkTaskRepository.findByRunIdOrderByLanguageAscDatasetAscToolAsc(runId);
                run.recalculateFromTasks(runTasks, now);
                benchmarkRunRepository.save(run);
            }

            meterRegistry.counter("benchmark.task.recovered_stale_running").increment(staleTasks.size());
            return staleTasks.size();
        });

        if (recoveredCount != null && recoveredCount > 0) {
            log.warn("Recovered {} stale RUNNING task(s) that exceeded the timeout window", recoveredCount);
        }
    }

    private List<BenchmarkTaskEntity> pickTasksFairly(List<BenchmarkTaskEntity> candidateTasks, int limit) {
        Map<UUID, Deque<BenchmarkTaskEntity>> tasksByRun = new LinkedHashMap<>();
        for (BenchmarkTaskEntity task : candidateTasks) {
            tasksByRun
                .computeIfAbsent(task.getRun().getId(), key -> new ArrayDeque<>())
                .addLast(task);
        }

        List<BenchmarkTaskEntity> selectedTasks = new ArrayList<>(Math.min(limit, candidateTasks.size()));
        boolean madeProgress = true;
        while (selectedTasks.size() < limit && madeProgress && !tasksByRun.isEmpty()) {
            madeProgress = false;
            var iterator = tasksByRun.entrySet().iterator();
            while (iterator.hasNext() && selectedTasks.size() < limit) {
                Map.Entry<UUID, Deque<BenchmarkTaskEntity>> entry = iterator.next();
                BenchmarkTaskEntity nextTask = entry.getValue().pollFirst();
                if (nextTask != null) {
                    selectedTasks.add(nextTask);
                    madeProgress = true;
                }
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }

        return selectedTasks;
    }

    private void executeClaimedTask(ClaimedTask claimedTask) {
        Instant executionStartedAt = Instant.now();
        CircuitBreakerState circuitBreakerState = circuitBreakerByTool.computeIfAbsent(claimedTask.tool(), key -> new CircuitBreakerState());

        MDC.put("runId", claimedTask.runId().toString());
        MDC.put("taskId", claimedTask.taskId().toString());
        MDC.put("tool", claimedTask.tool());
        try {
            if (circuitBreakerState.isOpen()) {
                completeTask(
                    claimedTask,
                    TaskStatus.SKIPPED_CIRCUIT_OPEN,
                    null,
                    "Skipped because circuit breaker is open for tool: " + claimedTask.tool(),
                    executionStartedAt,
                    false
                );
                return;
            }

            CompletableFuture<ToolExecutionResult> executionFuture = runOrchrestratorService.runTaskWithAppropriateTool(claimedTask.request());
            try {
                ToolExecutionResult result = executionFuture.get(benchmarkExecutionConfig.getTaskTimeoutMs(), TimeUnit.MILLISECONDS);
                circuitBreakerState.onSuccess();
                completeTask(claimedTask, TaskStatus.SUCCESS, result.result(), null, executionStartedAt, false);
            } catch (TimeoutException ex) {
                executionFuture.cancel(true);
                circuitBreakerState.onFailure(benchmarkExecutionConfig);
                handleFailure(claimedTask, TaskStatus.TIMEOUT, "Execution timed out after " + benchmarkExecutionConfig.getTaskTimeoutMs() + "ms", executionStartedAt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                handleFailure(claimedTask, TaskStatus.FAILED, "Execution interrupted", executionStartedAt);
            } catch (Exception ex) {
                circuitBreakerState.onFailure(benchmarkExecutionConfig);
                handleFailure(claimedTask, TaskStatus.FAILED, rootMessage(ex), executionStartedAt);
            }
        } finally {
            MDC.clear();
        }
    }

    private void handleFailure(ClaimedTask claimedTask, TaskStatus failureStatus, String errorMessage, Instant executionStartedAt) {
        boolean shouldRetry = claimedTask.attemptNumber() < claimedTask.maxAttempts();
        completeTask(claimedTask, failureStatus, null, errorMessage, executionStartedAt, shouldRetry);
    }

    private void completeTask(
        ClaimedTask claimedTask,
        TaskStatus completionStatus,
        String rawResult,
        String errorMessage,
        Instant executionStartedAt,
        boolean retryable
    ) {
        Instant now = Instant.now();
        long durationMs = Math.max(0L, now.toEpochMilli() - executionStartedAt.toEpochMilli());

        transactionTemplate.executeWithoutResult(status -> {
            BenchmarkTaskEntity task = benchmarkTaskRepository.findById(claimedTask.taskId()).orElseThrow();
            TaskAttemptEntity attempt = taskAttemptRepository.findById(claimedTask.attemptId()).orElseThrow();
            BenchmarkRunEntity run = task.getRun();

            if (retryable) {
                Instant nextRetryAt = now.plusMillis(Math.max(1L, benchmarkExecutionConfig.getRetryBackoffMs()));
                task.markRetryableFailure(completionStatus, errorMessage, now, nextRetryAt);
                run.markTaskPendingAfterRetry(nextRetryAt);
                benchmarkTaskRepository.save(task);
                benchmarkRunRepository.save(run);
                attempt.markCompleted(completionStatus, errorMessage, durationMs, now);
                log.warn("Task {} failed on attempt {} and was re-queued for retry at {}", claimedTask.taskId(), claimedTask.attemptNumber(), nextRetryAt);
                meterRegistry.counter("benchmark.task.retry", "tool", claimedTask.tool(), "status", completionStatus.name()).increment();
                return;
            }

            task.markTerminal(completionStatus, errorMessage, rawResult, now);
            run.markTaskTerminal(completionStatus, now);
            benchmarkTaskRepository.save(task);
            benchmarkRunRepository.save(run);
            attempt.markCompleted(completionStatus, errorMessage, durationMs, now);
            meterRegistry.counter("benchmark.task.completed", "tool", claimedTask.tool(), "status", completionStatus.name()).increment();
            Timer.builder("benchmark.task.duration")
                .tag("tool", claimedTask.tool())
                .tag("status", completionStatus.name())
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
            log.info("Task {} completed with status {} in {} ms", claimedTask.taskId(), completionStatus, durationMs);
        });
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Unknown error" : current.getMessage();
    }

    private long computeDurationMs(Instant startedAt, Instant completedAt) {
        if (startedAt == null || completedAt == null) {
            return 0L;
        }
        return Math.max(0L, completedAt.toEpochMilli() - startedAt.toEpochMilli());
    }

    private record ClaimedTask(
        UUID taskId,
        UUID runId,
        UUID attemptId,
        int attemptNumber,
        int maxAttempts,
        String tool,
        com.devashish.learning.benchmarking.models.ToolExecutionRequest request
    ) {}

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
}
