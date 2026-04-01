package com.devashish.learning.benchmarking.repositories;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devashish.learning.benchmarking.models.TaskStatus;
import com.devashish.learning.benchmarking.persistence.entities.BenchmarkTaskEntity;

public interface BenchmarkTaskRepository extends JpaRepository<BenchmarkTaskEntity, UUID> {

    List<BenchmarkTaskEntity> findByRunIdOrderByLanguageAscDatasetAscToolAsc(UUID runId);

    List<BenchmarkTaskEntity> findByRunIdAndStatus(UUID runId, TaskStatus status);

    @Query("""
        select task
        from BenchmarkTaskEntity task
        join fetch task.run run
        where task.language = :language
          and task.dataset = :dataset
          and task.tool = :tool
        order by run.submittedAt asc
        """)
    List<BenchmarkTaskEntity> findHistoricalTasksForChart(
        @Param("language") String language,
        @Param("dataset") String dataset,
        @Param("tool") String tool
    );

    @Query(value = """
        select *
        from benchmark_task
        where status = 'PENDING'
          and (next_retry_at is null or next_retry_at <= current_timestamp)
        order by created_at
        for update skip locked
        limit :limit
        """, nativeQuery = true)
    List<BenchmarkTaskEntity> findReadyTasksForClaim(@Param("limit") int limit);

    @Query(value = """
        select *
        from benchmark_task
        where status = 'RUNNING'
          and started_at is not null
          and started_at <= :cutoff
        order by started_at
        for update skip locked
        """, nativeQuery = true)
    List<BenchmarkTaskEntity> findStaleRunningTasks(@Param("cutoff") Instant cutoff);
}
