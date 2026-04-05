package com.devashish.learning.benchmarking.serivces;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.devashish.learning.benchmarking.exceptions.ResourceNotFoundException;
import com.devashish.learning.benchmarking.models.BenchmarkRunResponse;
import com.devashish.learning.benchmarking.models.BenchmarkRunSummaryResponse;
import com.devashish.learning.benchmarking.models.JobStatus;
import com.devashish.learning.benchmarking.models.JobStatusResponse;
import com.devashish.learning.benchmarking.models.PagedBenchmarkRunsResponse;
import com.devashish.learning.benchmarking.models.TaskProgressView;
import com.devashish.learning.benchmarking.persistence.entities.BenchmarkRunEntity;
import com.devashish.learning.benchmarking.persistence.entities.BenchmarkTaskEntity;
import com.devashish.learning.benchmarking.repositories.BenchmarkRunRepository;
import com.devashish.learning.benchmarking.repositories.BenchmarkTaskRepository;

@Service
public class BenchmarkRunQueryService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkTaskRepository benchmarkTaskRepository;

    public BenchmarkRunQueryService(BenchmarkRunRepository benchmarkRunRepository, BenchmarkTaskRepository benchmarkTaskRepository) {
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.benchmarkTaskRepository = benchmarkTaskRepository;
    }

    public JobStatusResponse getRunStatus(UUID runId) {
        BenchmarkRunEntity run = benchmarkRunRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("Run not found for runId: " + runId));
        List<BenchmarkTaskEntity> tasks = benchmarkTaskRepository.findByRunIdOrderByLanguageAscDatasetAscToolAsc(runId);
        return new JobStatusResponse(
            run.getId(),
            run.getRequestedLanguage(),
            run.getStatus(),
            run.isArchived(),
            run.getTotalTasks(),
            run.getPendingTasks(),
            run.getRunningTasks(),
            run.getSuccessTasks(),
            run.getFailedTasks(),
            run.getTimeoutTasks(),
            run.getSkippedTasks(),
            run.getCancelledTasks(),
            run.getSubmittedAt(),
            run.getStartedAt(),
            run.getCompletedAt(),
            run.getArchivedAt(),
            tasks.stream().map(this::toTaskProgressView).toList()
        );
    }

    public List<TaskProgressView> getRunTasks(UUID runId) {
        if (!benchmarkRunRepository.existsById(runId)) {
            throw new ResourceNotFoundException("Run not found for runId: " + runId);
        }
        return benchmarkTaskRepository.findByRunIdOrderByLanguageAscDatasetAscToolAsc(runId)
            .stream()
            .map(this::toTaskProgressView)
            .toList();
    }

    public BenchmarkRunResponse getRunResults(UUID runId) {
        if (!benchmarkRunRepository.existsById(runId)) {
            throw new ResourceNotFoundException("Run not found for runId: " + runId);
        }
        Map<String, Map<String, Map<String, String>>> nestedResults = new LinkedHashMap<>();
        for (BenchmarkTaskEntity task : benchmarkTaskRepository.findByRunIdOrderByLanguageAscDatasetAscToolAsc(runId)) {
            if (task.getStatus() == com.devashish.learning.benchmarking.models.TaskStatus.SUCCESS && task.getRawResult() != null) {
                nestedResults
                    .computeIfAbsent(task.getLanguage(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(task.getDataset(), key -> new LinkedHashMap<>())
                    .put(task.getTool(), task.getRawResult());
            }
        }
        return new BenchmarkRunResponse(nestedResults);
    }

    public PagedBenchmarkRunsResponse listRuns(int page, int size, String language, JobStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
        Specification<BenchmarkRunEntity> specification = Specification.where(null);

        if (language != null && !language.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(cb.lower(root.get("requestedLanguage")), language.trim().toLowerCase()));
        }
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<BenchmarkRunEntity> runs = benchmarkRunRepository.findAll(specification, pageable);
        return new PagedBenchmarkRunsResponse(
            runs.getContent().stream().map(this::toSummary).toList(),
            runs.getNumber(),
            runs.getSize(),
            runs.getTotalElements(),
            runs.getTotalPages(),
            runs.hasNext()
        );
    }

    private TaskProgressView toTaskProgressView(BenchmarkTaskEntity task) {
        return new TaskProgressView(
            task.getId().toString(),
            task.getLanguage(),
            task.getDataset(),
            task.getTool(),
            task.getStatus(),
            task.getAttemptCount(),
            task.getStartedAt(),
            task.getCompletedAt(),
            task.getErrorMessage()
        );
    }

    private BenchmarkRunSummaryResponse toSummary(BenchmarkRunEntity run) {
        return new BenchmarkRunSummaryResponse(
            run.getId(),
            run.getRequestedLanguage(),
            run.getStatus(),
            run.isArchived(),
            run.getTotalTasks(),
            run.getPendingTasks(),
            run.getRunningTasks(),
            run.getSuccessTasks(),
            run.getFailedTasks(),
            run.getTimeoutTasks(),
            run.getSkippedTasks(),
            run.getCancelledTasks(),
            run.getSubmittedAt(),
            run.getStartedAt(),
            run.getCompletedAt(),
            run.getArchivedAt()
        );
    }
}
