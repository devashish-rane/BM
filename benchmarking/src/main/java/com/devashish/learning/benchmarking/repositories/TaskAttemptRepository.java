package com.devashish.learning.benchmarking.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devashish.learning.benchmarking.persistence.entities.TaskAttemptEntity;

public interface TaskAttemptRepository extends JpaRepository<TaskAttemptEntity, UUID> {

    Optional<TaskAttemptEntity> findTopByTaskIdOrderByAttemptNumberDesc(UUID taskId);
}
