package com.devashish.learning.benchmarking.persistence.entities;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.devashish.learning.benchmarking.models.JobStatus;
import com.devashish.learning.benchmarking.models.TaskStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "benchmark_run")
public class BenchmarkRunEntity extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "requested_language", nullable = false)
    private String requestedLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "total_tasks", nullable = false)
    private int totalTasks;

    @Column(name = "pending_tasks", nullable = false)
    private int pendingTasks;

    @Column(name = "running_tasks", nullable = false)
    private int runningTasks;

    @Column(name = "success_tasks", nullable = false)
    private int successTasks;

    @Column(name = "failed_tasks", nullable = false)
    private int failedTasks;

    @Column(name = "timeout_tasks", nullable = false)
    private int timeoutTasks;

    @Column(name = "skipped_tasks", nullable = false)
    private int skippedTasks;

    @Column(name = "cancelled_tasks", nullable = false)
    private int cancelledTasks;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BenchmarkTaskEntity> tasks = new ArrayList<>();

    protected BenchmarkRunEntity() {
    }

    public BenchmarkRunEntity(UUID id, String requestedLanguage, String idempotencyKey, Instant submittedAt) {
        this.id = id;
        this.requestedLanguage = requestedLanguage;
        this.idempotencyKey = idempotencyKey;
        this.submittedAt = submittedAt;
        this.status = JobStatus.QUEUED;
        this.archived = false;
    }

    public void initializeTaskCounts(int totalTasks) {
        this.totalTasks = totalTasks;
        this.pendingTasks = totalTasks;
        this.runningTasks = 0;
        this.successTasks = 0;
        this.failedTasks = 0;
        this.timeoutTasks = 0;
        this.skippedTasks = 0;
        this.cancelledTasks = 0;
    }

    public void addTask(BenchmarkTaskEntity task) {
        tasks.add(task);
    }

    public void markTaskRunning(Instant now) {
        if (pendingTasks > 0) {
            pendingTasks--;
        }
        runningTasks++;
        if (status == JobStatus.QUEUED) {
            status = JobStatus.RUNNING;
            if (startedAt == null) {
                startedAt = now;
            }
        }
        touch();
    }

    public void markTaskPendingAfterRetry(Instant nextRetryAt) {
        if (runningTasks > 0) {
            runningTasks--;
        }
        pendingTasks++;
        touch();
    }

    public void markTaskTerminal(TaskStatus terminalStatus, Instant now) {
        if (runningTasks > 0) {
            runningTasks--;
        }
        switch (terminalStatus) {
            case SUCCESS -> successTasks++;
            case FAILED -> failedTasks++;
            case TIMEOUT -> timeoutTasks++;
            case SKIPPED_CIRCUIT_OPEN -> skippedTasks++;
            case CANCELLED -> cancelledTasks++;
            default -> throw new IllegalArgumentException("Unsupported terminal status: " + terminalStatus);
        }
        updateTerminalRunStatus(now);
    }

    public void markPendingTaskCancelled(Instant now) {
        if (pendingTasks > 0) {
            pendingTasks--;
        }
        cancelledTasks++;
        updateTerminalRunStatus(now);
    }

    public void cancel(Instant now) {
        this.status = JobStatus.CANCELLED;
        if (completedAt == null && pendingTasks == 0 && runningTasks == 0) {
            completedAt = now;
        }
        touch();
    }

    public void recalculateFromTasks(List<BenchmarkTaskEntity> taskEntities, Instant now) {
        this.totalTasks = taskEntities.size();
        this.pendingTasks = 0;
        this.runningTasks = 0;
        this.successTasks = 0;
        this.failedTasks = 0;
        this.timeoutTasks = 0;
        this.skippedTasks = 0;
        this.cancelledTasks = 0;

        for (BenchmarkTaskEntity taskEntity : taskEntities) {
            switch (taskEntity.getStatus()) {
                case PENDING -> pendingTasks++;
                case RUNNING -> runningTasks++;
                case SUCCESS -> successTasks++;
                case FAILED -> failedTasks++;
                case TIMEOUT -> timeoutTasks++;
                case SKIPPED_CIRCUIT_OPEN -> skippedTasks++;
                case CANCELLED -> cancelledTasks++;
            }
        }

        if (runningTasks > 0) {
            this.status = JobStatus.RUNNING;
            if (startedAt == null) {
                startedAt = now;
            }
            this.completedAt = null;
        } else if (pendingTasks > 0) {
            this.status = JobStatus.QUEUED;
            this.completedAt = null;
        } else if (successTasks == totalTasks) {
            this.status = JobStatus.COMPLETED;
            this.completedAt = now;
        } else if (successTasks > 0) {
            this.status = JobStatus.PARTIALLY_COMPLETED;
            this.completedAt = now;
        } else if (failedTasks + timeoutTasks + skippedTasks + cancelledTasks == totalTasks) {
            this.status = JobStatus.FAILED;
            this.completedAt = now;
        } else {
            this.status = JobStatus.PARTIALLY_COMPLETED;
            this.completedAt = now;
        }
        touch();
    }

    public void archive(Instant now) {
        this.archived = true;
        this.archivedAt = now;
        touch();
    }

    public boolean isTerminal() {
        return status == JobStatus.COMPLETED
            || status == JobStatus.PARTIALLY_COMPLETED
            || status == JobStatus.FAILED
            || status == JobStatus.CANCELLED;
    }

    private void updateTerminalRunStatus(Instant now) {
        if (pendingTasks == 0 && runningTasks == 0) {
            completedAt = now;
            if (status == JobStatus.CANCELLED) {
                touch();
                return;
            }
            if (successTasks == totalTasks) {
                status = JobStatus.COMPLETED;
            } else if (successTasks > 0) {
                status = JobStatus.PARTIALLY_COMPLETED;
            } else if (failedTasks + timeoutTasks + skippedTasks + cancelledTasks == totalTasks) {
                status = JobStatus.FAILED;
            } else {
                status = JobStatus.PARTIALLY_COMPLETED;
            }
        }
        touch();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestedLanguage() {
        return requestedLanguage;
    }

    public JobStatus getStatus() {
        return status;
    }

    public boolean isArchived() {
        return archived;
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public int getPendingTasks() {
        return pendingTasks;
    }

    public int getRunningTasks() {
        return runningTasks;
    }

    public int getSuccessTasks() {
        return successTasks;
    }

    public int getFailedTasks() {
        return failedTasks;
    }

    public int getTimeoutTasks() {
        return timeoutTasks;
    }

    public int getSkippedTasks() {
        return skippedTasks;
    }

    public int getCancelledTasks() {
        return cancelledTasks;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public List<BenchmarkTaskEntity> getTasks() {
        return tasks;
    }
}
