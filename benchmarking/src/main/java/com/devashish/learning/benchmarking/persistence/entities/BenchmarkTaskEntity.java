package com.devashish.learning.benchmarking.persistence.entities;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.devashish.learning.benchmarking.models.TaskStatus;
import com.devashish.learning.benchmarking.models.ToolExecutionRequest;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "benchmark_task")
public class BenchmarkTaskEntity extends AuditableEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private BenchmarkRunEntity run;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private String dataset;

    @Column(nullable = false)
    private String tool;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "raw_result", columnDefinition = "TEXT")
    private String rawResult;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskAttemptEntity> attempts = new ArrayList<>();

    protected BenchmarkTaskEntity() {
    }

    public BenchmarkTaskEntity(UUID id, BenchmarkRunEntity run, String language, String dataset, String tool, int maxAttempts) {
        this.id = id;
        this.run = run;
        this.language = language;
        this.dataset = dataset;
        this.tool = tool;
        this.maxAttempts = maxAttempts;
        this.status = TaskStatus.PENDING;
        this.attemptCount = 0;
    }

    public void markRunning(Instant now) {
        this.status = TaskStatus.RUNNING;
        this.attemptCount++;
        if (startedAt == null) {
            startedAt = now;
        }
        this.errorMessage = null;
        this.nextRetryAt = null;
        touch();
    }

    public void markSuccess(String rawResult, Instant now) {
        this.status = TaskStatus.SUCCESS;
        this.rawResult = rawResult;
        this.errorMessage = null;
        this.completedAt = now;
        this.nextRetryAt = null;
        touch();
    }

    public void markRetryableFailure(TaskStatus status, String errorMessage, Instant now, Instant nextRetryAt) {
        this.status = TaskStatus.PENDING;
        this.errorMessage = errorMessage;
        this.lastFailureAt = now;
        this.nextRetryAt = nextRetryAt;
        this.rawResult = null;
        touch();
    }

    public void markTerminal(TaskStatus status, String errorMessage, String rawResult, Instant now) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.rawResult = rawResult;
        this.lastFailureAt = status == TaskStatus.SUCCESS ? lastFailureAt : now;
        this.completedAt = now;
        this.nextRetryAt = null;
        touch();
    }

    public void markCancelled(Instant now, String errorMessage) {
        this.status = TaskStatus.CANCELLED;
        this.errorMessage = errorMessage;
        this.completedAt = now;
        this.nextRetryAt = null;
        touch();
    }

    public boolean isFailureLike() {
        return status == TaskStatus.FAILED
            || status == TaskStatus.TIMEOUT
            || status == TaskStatus.SKIPPED_CIRCUIT_OPEN
            || status == TaskStatus.CANCELLED;
    }

    public boolean isTerminal() {
        return status != TaskStatus.PENDING && status != TaskStatus.RUNNING;
    }

    public void prepareForManualResume(int additionalAttempts) {
        this.status = TaskStatus.PENDING;
        this.maxAttempts = Math.max(this.maxAttempts, this.attemptCount + Math.max(1, additionalAttempts));
        this.errorMessage = null;
        this.rawResult = null;
        this.nextRetryAt = null;
        this.startedAt = null;
        this.completedAt = null;
        touch();
    }

    public boolean hasRemainingAttempts() {
        return attemptCount < maxAttempts;
    }

    public void addAttempt(TaskAttemptEntity attempt) {
        this.attempts.add(attempt);
    }

    public ToolExecutionRequest toExecutionRequest() {
        return new ToolExecutionRequest(language, dataset, tool);
    }

    public UUID getId() {
        return id;
    }

    public BenchmarkRunEntity getRun() {
        return run;
    }

    public String getLanguage() {
        return language;
    }

    public String getDataset() {
        return dataset;
    }

    public String getTool() {
        return tool;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRawResult() {
        return rawResult;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<TaskAttemptEntity> getAttempts() {
        return attempts;
    }
}
