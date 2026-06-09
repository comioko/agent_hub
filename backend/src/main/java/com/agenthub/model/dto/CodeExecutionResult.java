package com.agenthub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecutionResult {
    private boolean success;
    private String output;
    private long executionTimeMs;
}
