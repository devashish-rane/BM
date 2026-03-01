package com.devashish.learning.benchmarking.configs;

import java.util.ArrayList;
import java.util.HashMap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="execution-matrix")
public record ExecutionMatrixConfig(HashMap<String, HashMap<String,ArrayList<String>>> language){}
