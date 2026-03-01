package com.devashish.learning.benchmarking.configs;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CommonThreadPoolConfig {
    
    @Bean
    public Executor toolExecutorPool(){
        ThreadPoolTaskExecutor tpte = new ThreadPoolTaskExecutor();
        tpte.setCorePoolSize(4);
        tpte.setMaxPoolSize(8);
        tpte.setQueueCapacity(100);
        tpte.setKeepAliveSeconds(60);
        tpte.setThreadNamePrefix("async-tool-exec");
        tpte.initialize();
        return tpte;
    }

    @Bean
    public Executor jobOrchestratorExecutor() {
        ThreadPoolTaskExecutor tpte = new ThreadPoolTaskExecutor();
        tpte.setCorePoolSize(4);
        tpte.setMaxPoolSize(8);
        tpte.setQueueCapacity(200);
        tpte.setKeepAliveSeconds(60);
        tpte.setThreadNamePrefix("job-orchestrator-");
        tpte.initialize();
        return tpte;
    }
}
