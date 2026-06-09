package com.agenthub.agent.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents a tool call request or response.
 * Follows OpenAI Function Calling format.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolCall {

    /**
     * The unique ID of this tool call.
     */
    private String id;

    /**
     * The type of tool call. Currently only "function" is supported.
     */
    private String type = "function";

    /**
     * The function that was called.
     */
    private Function function;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Function {
        /**
         * The name of the function.
         */
        private String name;

        /**
         * The arguments to the function (JSON string).
         */
        private String arguments;
    }

    /**
     * Get arguments as a parsed Map.
     */
    public java.util.Map<String, Object> getArgumentsMap() {
        if (function == null || function.getArguments() == null) {
            return new java.util.HashMap<>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(function.getArguments(), java.util.Map.class);
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }
}
