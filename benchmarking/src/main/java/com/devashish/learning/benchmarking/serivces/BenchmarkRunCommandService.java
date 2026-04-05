package com.devashish.learning.benchmarking.serivces;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devashish.learning.benchmarking.DTOs.BenchRunSubmitRequest;
import com.devashish.learning.benchmarking.DTOs.ResumeRunRequest;
import com.devashish.learning.benchmarking.configs.BenchmarkExecutionConfig;
import com.devashish.learning.benchmarking.exceptions.BadRequestException;
import com.devashish.learning.benchmarking.exceptions.ConflictException;
import com.devashish.learning.benchmarking.exceptions.ResourceNotFoundException;
import com.devashish.learning.benchmarking.models.JobStatus;
import com.devashish.learning.benchmarking.models.JobStatusResponse;
import com.devashish.learning.benchmarking.models.JobSubmitResponse;
import com.devashish.learning.benchmarking.models.TaskStatus;
import com.devashish.learning.benchmarking.models.ToolExecutionRequest;
import com.devashish.learning.benchmarking.persistence.entities.BenchmarkRunEntity;
import com.devashish.learning.benchmarking.persistence.entities.BenchmarkTaskEntity;
import com.devashish.learning.benchmarking.repositories.BenchmarkRunRepository;
import com.devashish.learning.benchmarking.repositories.BenchmarkTaskRepository;

@Service
public class BenchmarkRunCommandService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunCommandService.class);

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkTaskRepository benchmarkTaskRepository;
    private final BenchmarkTaskPlanService benchmarkTaskPlanService;
    private final BenchmarkExecutionConfig benchmarkExecutionConfig;
    private final BenchmarkRunQueryService benchmarkRunQueryService;

    public BenchmarkRunCommandService(
        BenchmarkRunRepository benchmarkRunRepository,
        BenchmarkTaskRepository benchmarkTaskRepository,
        BenchmarkTaskPlanService benchmarkTaskPlanService,
        BenchmarkExecutionConfig benchmarkExecutionConfig,
        BenchmarkRunQueryService benchmarkRunQueryService
    ) {
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.benchmarkTaskRepository = benchmarkTaskRepository;
        this.benchmarkTaskPlanService = benchmarkTaskPlanService;
        this.benchmarkExecutionConfig = benchmarkExecutionConfig;
        this.benchmarkRunQueryService = benchmarkRunQueryService;
    }

    @Transactional
    public JobSubmitResponse submitRun(BenchRunSubmitRequest request, String idempotencyKey) {
        String requestedLanguage = normalizeLanguage(request.language());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return benchmarkRunRepository.findByIdempotencyKey(idempotencyKey.trim())
                .map(this::toSubmitResponse)
                .orElseGet(() -> createRun(request, requestedLanguage, idempotencyKey.trim()));
        }

        return createRun(request, requestedLanguage, null);
    }

    @Transactional
    public JobStatusResponse cancelRun(UUID runId) {
        BenchmarkRunEntity run = benchmarkRunRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("Run not found for runId: " + runId));

        if (run.isArchived()) {
            throw new ConflictException("Archived runs cannot be changed: " + runId);
        }

        if (run.isTerminal()) {
            throw new ConflictException("Run is already terminal and cannot be cancelled: " + runId);
        }

        Instant now = Instant.now();
        List<BenchmarkTaskEntity> pendingTasks = benchmarkTaskRepository.findByRunIdAndStatus(runId, TaskStatus.PENDING);
        for (BenchmarkTaskEntity task : pendingTasks) {
            task.markCancelled(now, "Cancelled before execution");
            run.markPendingTaskCancelled(now);
        }
        run.cancel(now);
        log.info("Cancelled benchmark run {} with {} pending tasks marked cancelled", runId, pendingTasks.size());
        return benchmarkRunQueryService.getRunStatus(runId);
    }

    @Transactional
    public JobStatusResponse archiveRun(UUID runId) {
        BenchmarkRunEntity run = benchmarkRunRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("Run not found for runId: " + runId));

        if (run.isArchived()) {
            throw new ConflictException("Run is already archived: " + runId);
        }
        if (!run.isTerminal()) {
            throw new ConflictException("Only terminal runs can be archived: " + runId);
        }

        run.archive(Instant.now());
        log.info("Archived benchmark run {}", runId);
        return benchmarkRunQueryService.getRunStatus(runId);
    }

    @Transactional
    public JobStatusResponse resumeRun(UUID runId, ResumeRunRequest request) {
        BenchmarkRunEntity run = benchmarkRunRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("Run not found for runId: " + runId));

        if (run.isArchived()) {
            throw new ConflictException("Archived runs cannot be resumed: " + runId);
        }
        if (run.getStatus() == JobStatus.RUNNING) {
            throw new ConflictException("Running runs cannot be resumed: " + runId);
        }

        List<BenchmarkTaskEntity> tasks = benchmarkTaskRepository.findByRunIdOrderByLanguageAscDatasetAscToolAsc(runId);
        Set<String> requestedLanguages = normalizeFilterValues(request == null ? null : request.languages());
        Set<String> requestedDatasets = normalizeFilterValues(request == null ? null : request.datasets());
        Set<String> requestedTools = normalizeFilterValues(request == null ? null : request.tools());
        boolean failedOnly = request == null || request.failedOnly() == null || request.failedOnly();

        List<BenchmarkTaskEntity> selectedTasks = tasks.stream()
            .filter(task -> matches(requestedLanguages, task.getLanguage()))
            .filter(task -> matches(requestedDatasets, task.getDataset()))
            .filter(task -> matches(requestedTools, task.getTool()))
            .filter(task -> task.isTerminal())
            .filter(task -> !failedOnly || task.isFailureLike())
            .toList();

        if (selectedTasks.isEmpty()) {
            throw new BadRequestException("No eligible tasks matched the resume selection");
        }

        int additionalAttempts = Math.max(1, benchmarkExecutionConfig.getMaxRetries() + 1);
        selectedTasks.forEach(task -> task.prepareForManualResume(additionalAttempts));
        benchmarkTaskRepository.saveAll(selectedTasks);
        run.recalculateFromTasks(tasks, Instant.now());
        benchmarkRunRepository.save(run);

        log.info(
            "Resumed {} task(s) for run {} with filters languages={}, datasets={}, tools={}, failedOnly={}",
            selectedTasks.size(),
            runId,
            requestedLanguages,
            requestedDatasets,
            requestedTools,
            failedOnly
        );
        return benchmarkRunQueryService.getRunStatus(runId);
    }

    private JobSubmitResponse createRun(BenchRunSubmitRequest request, String requestedLanguage, String idempotencyKey) {
        List<ToolExecutionRequest> plannedTasks = benchmarkTaskPlanService.planTasks(requestedLanguage);
        if (plannedTasks.isEmpty()) {
            throw new BadRequestException("No tasks found for language: " + requestedLanguage);
        }

        UUID runId = request.runid() == null ? UUID.randomUUID() : request.runid();
        if (benchmarkRunRepository.existsById(runId)) {
            throw new ConflictException("Run already exists for runId: " + runId);
        }

        Instant submittedAt = Instant.now();
        BenchmarkRunEntity run = new BenchmarkRunEntity(runId, requestedLanguage, idempotencyKey, submittedAt);
        run.initializeTaskCounts(plannedTasks.size());
        benchmarkRunRepository.save(run);

        int maxAttempts = Math.max(1, benchmarkExecutionConfig.getMaxRetries() + 1);
        List<BenchmarkTaskEntity> taskEntities = plannedTasks.stream()
            .map(task -> new BenchmarkTaskEntity(UUID.randomUUID(), run, task.language(), task.dataset(), task.tool(), maxAttempts))
            .toList();
        taskEntities.forEach(run::addTask);
        benchmarkTaskRepository.saveAll(taskEntities);

        log.info("Submitted benchmark run {} for language {} with {} tasks", runId, requestedLanguage, taskEntities.size());
        return toSubmitResponse(run);
    }

    private JobSubmitResponse toSubmitResponse(BenchmarkRunEntity run) {
        return new JobSubmitResponse(run.getId(), run.getStatus(), run.getTotalTasks(), run.getSubmittedAt());
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new BadRequestException("language must not be blank");
        }
        return language.trim();
    }

    private Set<String> normalizeFilterValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedValues = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalizedValues.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalizedValues;
    }

    private boolean matches(Set<String> filters, String actualValue) {
        return filters.isEmpty() || filters.contains(actualValue.toLowerCase(Locale.ROOT));
    }
}
