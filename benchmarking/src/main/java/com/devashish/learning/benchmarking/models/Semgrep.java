package com.devashish.learning.benchmarking.models;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.devashish.learning.benchmarking.interfaces.ITool;

@Component("semgrep")
public class Semgrep implements ITool{

    @Value("classpath:dummy-data/semgrep-response.json")
    private Resource semgrepDummyFile;

    public String executeTool(Task task){
        
        try{
            Thread.sleep(2000);
        }
        catch(InterruptedException ex){
            Thread.currentThread().interrupt();
            return "Semgrep interrupted";
        }

        try (InputStream inputStream = semgrepDummyFile.getInputStream()) {
            String response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return response.isBlank() ? "[]" : response;
        } catch (IOException ex) {
            return "[]";
        }
    }
}
