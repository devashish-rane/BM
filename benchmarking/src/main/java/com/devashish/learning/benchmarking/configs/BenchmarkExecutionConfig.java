package com.devashish.learning.benchmarking.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchmark.execution")
public class BenchmarkExecutionConfig {

    private int maxConcurrency = 6;
    private int maxRetries = 1;
    private long taskTimeoutMs = 600000;
    private int circuitBreakerFailureThreshold = 3;
    private long circuitBreakerOpenMs = 60000;
    private long retryBackoffMs = 5000;
    private int workerBatchSize = 6;
    private int claimWindowSize = 100;
    private long staleRunningTaskGraceMs = 30000;

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getTaskTimeoutMs() {
        return taskTimeoutMs;
    }

    public void setTaskTimeoutMs(long taskTimeoutMs) {
        this.taskTimeoutMs = taskTimeoutMs;
    }

    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
    }

    public long getCircuitBreakerOpenMs() {
        return circuitBreakerOpenMs;
    }

    public void setCircuitBreakerOpenMs(long circuitBreakerOpenMs) {
        this.circuitBreakerOpenMs = circuitBreakerOpenMs;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public int getWorkerBatchSize() {
        return workerBatchSize;
    }

    public void setWorkerBatchSize(int workerBatchSize) {
        this.workerBatchSize = workerBatchSize;
    }

    public int getClaimWindowSize() {
        return claimWindowSize;
    }

    public void setClaimWindowSize(int claimWindowSize) {
        this.claimWindowSize = claimWindowSize;
    }

    public long getStaleRunningTaskGraceMs() {
        return staleRunningTaskGraceMs;
    }

    public void setStaleRunningTaskGraceMs(long staleRunningTaskGraceMs) {
        this.staleRunningTaskGraceMs = staleRunningTaskGraceMs;
    }
}
