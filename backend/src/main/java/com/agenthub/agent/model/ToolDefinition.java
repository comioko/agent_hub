package com.agenthub.agent.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Tool definition following OpenAI Function Calling format.
 * Represents a tool that the agent can call.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolDefinition {

    /**
     * The type of the tool. Currently only "function" is supported.
     */
    private String type = "function";

    /**
     * The function tool definition.
     */
    private Function function;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Function {
        /**
         * The name of the function to be called.
         */
        private String name;

        /**
         * A description of what the function does.
         */
        private String description;

        /**
         * The parameters the function accepts (JSON Schema).
         */
        private Parameters parameters;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Parameters {
        /**
         * Must be "object" for OpenAI compatibility.
         */
        private String type = "object";

        /**
         * Properties of the function parameters.
         */
        private java.util.Map<String, ParameterProperty> properties;

        /**
         * Required parameter names.
         */
        private java.util.List<String> required;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ParameterProperty {
        private String type;
        private String description;
        private String[] enumValues;
    }

    /**
     * Helper method to create a simple bash tool definition.
     */
    public static ToolDefinition bashTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setType("function");

        Function func = new Function();
        func.setName("bash");
        func.setDescription("Execute a bash/shell command and return the output.");

        Parameters params = new Parameters();
        params.setType("object");

        java.util.Map<String, ParameterProperty> props = new java.util.HashMap<>();

        ParameterProperty command = new ParameterProperty();
        command.setType("string");
        command.setDescription("The bash command to execute");
        props.put("command", command);

        params.setProperties(props);
        params.setRequired(java.util.Arrays.asList("command"));

        func.setParameters(params);
        tool.setFunction(func);

        return tool;
    }

    /**
     * Helper method to create a read_file tool definition.
     */
    public static ToolDefinition readFileTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setType("function");

        Function func = new Function();
        func.setName("read_file");
        func.setDescription("Read the contents of a file from the filesystem.");

        Parameters params = new Parameters();
        params.setType("object");

        java.util.Map<String, ParameterProperty> props = new java.util.HashMap<>();

        ParameterProperty path = new ParameterProperty();
        path.setType("string");
        path.setDescription("The absolute path to the file to read");
        props.put("path", path);

        params.setProperties(props);
        params.setRequired(java.util.Arrays.asList("path"));

        func.setParameters(params);
        tool.setFunction(func);

        return tool;
    }

    /**
     * Helper method to create a write_file tool definition.
     */
    public static ToolDefinition writeFileTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setType("function");

        Function func = new Function();
        func.setName("write_file");
        func.setDescription("Write content to a file. Creates the file if it doesn't exist.");

        Parameters params = new Parameters();
        params.setType("object");

        java.util.Map<String, ParameterProperty> props = new java.util.HashMap<>();

        ParameterProperty path = new ParameterProperty();
        path.setType("string");
        path.setDescription("The absolute path to the file to write");
        props.put("path", path);

        ParameterProperty content = new ParameterProperty();
        content.setType("string");
        content.setDescription("The content to write to the file");
        props.put("content", content);

        params.setProperties(props);
        params.setRequired(java.util.Arrays.asList("path", "content"));

        func.setParameters(params);
        tool.setFunction(func);

        return tool;
    }

    /**
     * Helper method to create a glob tool definition.
     */
    public static ToolDefinition globTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setType("function");

        Function func = new Function();
        func.setName("glob");
        func.setDescription("Find files matching a glob pattern.");

        Parameters params = new Parameters();
        params.setType("object");

        java.util.Map<String, ParameterProperty> props = new java.util.HashMap<>();

        ParameterProperty pattern = new ParameterProperty();
        pattern.setType("string");
        pattern.setDescription("The glob pattern to match (e.g., **/*.java)");
        props.put("pattern", pattern);

        params.setProperties(props);
        params.setRequired(java.util.Arrays.asList("pattern"));

        func.setParameters(params);
        tool.setFunction(func);

        return tool;
    }

    /**
     * Helper method to create a grep tool definition.
     */
    public static ToolDefinition grepTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setType("function");

        Function func = new Function();
        func.setName("grep");
        func.setDescription("Search for text patterns in files.");

        Parameters params = new Parameters();
        params.setType("object");

        java.util.Map<String, ParameterProperty> props = new java.util.HashMap<>();

        ParameterProperty pattern = new ParameterProperty();
        pattern.setType("string");
        pattern.setDescription("The text pattern to search for");
        props.put("pattern", pattern);

        ParameterProperty path = new ParameterProperty();
        path.setType("string");
        path.setDescription("The directory or file path to search in");
        props.put("path", path);

        params.setProperties(props);
        params.setRequired(java.util.Arrays.asList("pattern"));

        func.setParameters(params);
        tool.setFunction(func);

        return tool;
    }
}
