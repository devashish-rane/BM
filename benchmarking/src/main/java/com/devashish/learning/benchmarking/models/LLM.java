package com.devashish.learning.benchmarking.models;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.devashish.learning.benchmarking.interfaces.ITool;

@Component("llm")
public class LLM implements ITool {

    @Value("classpath:dummy-data/llm-response.json")
    private Resource llmDummyFile;

    public String executeTool(Task task){
        try (InputStream inputStream = llmDummyFile.getInputStream()) {
            String response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return response.isBlank() ? "[]" : response;
        } catch (IOException ex) {
            return "[]";
        }
    }
}
