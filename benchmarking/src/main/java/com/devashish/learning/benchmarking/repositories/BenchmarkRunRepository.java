package com.devashish.learning.benchmarking.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.devashish.learning.benchmarking.persistence.entities.BenchmarkRunEntity;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRunEntity, UUID>, JpaSpecificationExecutor<BenchmarkRunEntity> {

    Optional<BenchmarkRunEntity> findByIdempotencyKey(String idempotencyKey);
}
