package com.agenthub.agent.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Result of a tool execution.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolExecutionResult {

    /**
     * The ID of the tool call this result is for.
     */
    private String callId;

    /**
     * The name of the tool that was executed.
     */
    private String toolName;

    /**
     * Whether the tool execution was successful.
     */
    private boolean success;

    /**
     * The result content (output from the tool).
     */
    private String result;

    /**
     * Error message if execution failed.
     */
    private String error;

    /**
     * Create a successful result.
     */
    public static ToolExecutionResult success(String callId, String toolName, String result) {
        return new ToolExecutionResult(callId, toolName, true, result, null);
    }

    /**
     * Create a failed result.
     */
    public static ToolExecutionResult failure(String callId, String toolName, String error) {
        return new ToolExecutionResult(callId, toolName, false, null, error);
    }

    /**
     * Convert to message format for sending back to the LLM.
     */
    public Map<String, Object> toMessage() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", callId);
        msg.put("name", toolName);
        msg.put("content", success ? result : "Error: " + error);
        return msg;
    }
}
