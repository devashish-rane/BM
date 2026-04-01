package com.devashish.learning.benchmarking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class BenchmarkingApplication {

	public static void main(String[] args) {
		SpringApplication.run(BenchmarkingApplication.class, args);
	}
}
