package com.devashish.learning.benchmarking.persistence.entities;

import java.time.Instant;
import java.util.UUID;

import com.devashish.learning.benchmarking.models.TaskStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_attempt")
public class TaskAttemptEntity extends AuditableEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private BenchmarkTaskEntity task;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TaskAttemptEntity() {
    }

    public TaskAttemptEntity(UUID id, BenchmarkTaskEntity task, int attemptNumber, TaskStatus status, Instant startedAt) {
        this.id = id;
        this.task = task;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.startedAt = startedAt;
    }

    public void markCompleted(TaskStatus status, String errorMessage, Long durationMs, Instant completedAt) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.completedAt = completedAt;
        touch();
    }

    public UUID getId() {
        return id;
    }

    public BenchmarkTaskEntity getTask() {
        return task;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
