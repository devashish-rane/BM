package com.devashish.learning.benchmarking.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchmark.execution")
public class BenchmarkExecutionConfig {

    private int maxConcurrency = 6;
    private int maxRetries = 1;
    private long taskTimeoutMs = 600000;
    private int circuitBreakerFailureThreshold = 3;
    private long circuitBreakerOpenMs = 60000;

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
}
