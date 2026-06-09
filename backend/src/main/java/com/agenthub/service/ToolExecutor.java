package com.agenthub.service;

import com.agenthub.agent.model.ToolCall;
import com.agenthub.agent.model.ToolDefinition;
import com.agenthub.agent.model.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Service for executing tool calls.
 * Provides built-in tools: bash, read_file, write_file, glob, grep
 */
@Service
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    /**
     * Registry of available tool executors.
     */
    private final Map<String, ToolHandler> toolRegistry = new ConcurrentHashMap<>();

    public ToolExecutor() {
        registerBuiltInTools();
    }

    /**
     * Register built-in tool handlers.
     */
    private void registerBuiltInTools() {
        // bash - execute shell commands
        toolRegistry.put("bash", new ToolHandler() {
            @Override
            public ToolExecutionResult execute(ToolCall call, Map<String, Object> args) {
                String command = (String) args.get("command");
                if (command == null || command.isEmpty()) {
                    return ToolExecutionResult.failure(call.getId(), call.getFunction().getName(), "Command is required");
                }
                return executeBash(command);
            }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.bashTool();
            }
        });

        // read_file - read file contents
        toolRegistry.put("read_file", new ToolHandler() {
            @Override
            public ToolExecutionResult execute(ToolCall call, Map<String, Object> args) {
                String path = (String) args.get("path");
                if (path == null || path.isEmpty()) {
                    return ToolExecutionResult.failure(call.getId(), call.getFunction().getName(), "Path is required");
                }
                return readFile(path);
            }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.readFileTool();
            }
        });

        // write_file - write content to file
        toolRegistry.put("write_file", new ToolHandler() {
            @Override
            public ToolExecutionResult execute(ToolCall call, Map<String, Object> args) {
                String path = (String) args.get("path");
                String content = (String) args.get("content");
                if (path == null || path.isEmpty()) {
                    return ToolExecutionResult.failure(call.getId(), call.getFunction().getName(), "Path is required");
                }
                if (content == null) {
                    return ToolExecutionResult.failure(call.getId(), call.getFunction().getName(), "Content is required");
                }
                return writeFile(path, content);
            }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.writeFileTool();
            }
        });

        // glob - find files matching pattern
        toolRegistry.put("glob", new ToolHandler() {
            @Override
            public ToolExecutionResult execute(ToolCall call, Map<String, Object> args) {
                String pattern = (String) args.get("pattern");
                if (pattern == null || pattern.isEmpty()) {
                    return ToolExecutionResult.failure(call.getId(), call.getFunction().getName(), "Pattern is required");
                }
                return globFiles(pattern);
            }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.globTool();
            }
        });

        // grep - search for text in files
        toolRegistry.put("grep", new ToolHandler() {
            @Override
            public ToolExecutionResult execute(ToolCall call, Map<String, Object> args) {
                String pattern = (String) args.get("pattern");
                String path = (String) args.get("path");
                if (pattern == null || pattern.isEmpty()) {
                    return ToolExecutionResult.failure(call.getId(), call.getFunction().getName(), "Pattern is required");
                }
                return grep(pattern, path);
            }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.grepTool();
            }
        });
    }

    /**
     * Execute a tool call.
     */
    public ToolExecutionResult execute(ToolCall call) {
        if (call == null || call.getFunction() == null) {
            return ToolExecutionResult.failure("unknown", "unknown", "Invalid tool call");
        }

        String toolName = call.getFunction().getName();
        ToolHandler handler = toolRegistry.get(toolName);

        if (handler == null) {
            return ToolExecutionResult.failure(call.getId(), toolName, "Unknown tool: " + toolName);
        }

        Map<String, Object> args = call.getArgumentsMap();
        return handler.execute(call, args);
    }

    /**
     * Execute a tool by name with arguments.
     */
    public ToolExecutionResult execute(String toolName, Map<String, Object> args) {
        ToolHandler handler = toolRegistry.get(toolName);
        if (handler == null) {
            return ToolExecutionResult.failure("direct", toolName, "Unknown tool: " + toolName);
        }

        ToolCall fakeCall = new ToolCall();
        fakeCall.setId("direct-" + System.currentTimeMillis());
        fakeCall.setType("function");
        ToolCall.Function func = new ToolCall.Function();
        func.setName(toolName);
        fakeCall.setFunction(func);

        return handler.execute(fakeCall, args);
    }

    /**
     * Get all available tool definitions.
     */
    public List<ToolDefinition> getAvailableTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        for (ToolHandler handler : toolRegistry.values()) {
            tools.add(handler.getDefinition());
        }
        return tools;
    }

    /**
     * Get tool definitions by name.
     */
    public List<ToolDefinition> getToolsByName(List<String> names) {
        if (names == null || names.isEmpty()) {
            return getAvailableTools();
        }

        List<ToolDefinition> tools = new ArrayList<>();
        for (String name : names) {
            ToolHandler handler = toolRegistry.get(name);
            if (handler != null) {
                tools.add(handler.getDefinition());
            }
        }
        return tools;
    }

    /**
     * Check if a tool is available.
     */
    public boolean isToolAvailable(String toolName) {
        return toolRegistry.containsKey(toolName);
    }

    /**
     * Register a custom tool.
     */
    public void registerTool(String name, ToolHandler handler) {
        toolRegistry.put(name, handler);
    }

    // ==================== Built-in Tool Implementations ====================

    private ToolExecutionResult executeBash(String command) {
        try {
            log.info("Executing bash command: {}", command);

            ProcessBuilder pb = new ProcessBuilder();
            pb.command("bash", "-c", command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            String result = output.toString().trim();

            if (exitCode != 0) {
                return ToolExecutionResult.success("bash-" + System.currentTimeMillis(), "bash",
                    "Exit code: " + exitCode + "\n" + result);
            }

            return ToolExecutionResult.success("bash-" + System.currentTimeMillis(), "bash", result);

        } catch (Exception e) {
            log.error("Bash execution failed", e);
            return ToolExecutionResult.failure("bash-" + System.currentTimeMillis(), "bash", "Error: " + e.getMessage());
        }
    }

    private ToolExecutionResult readFile(String path) {
        try {
            log.info("Reading file: {}", path);
            Path filePath = Paths.get(path);

            if (!Files.exists(filePath)) {
                return ToolExecutionResult.failure("read-" + System.currentTimeMillis(), "read_file", "File not found: " + path);
            }

            String content = Files.readString(filePath);
            return ToolExecutionResult.success("read-" + System.currentTimeMillis(), "read_file", content);

        } catch (Exception e) {
            log.error("Read file failed", e);
            return ToolExecutionResult.failure("read-" + System.currentTimeMillis(), "read_file", "Error: " + e.getMessage());
        }
    }

    private ToolExecutionResult writeFile(String path, String content) {
        try {
            log.info("Writing file: {}", path);
            Path filePath = Paths.get(path);

            // Create parent directories if they don't exist
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }

            Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return ToolExecutionResult.success("write-" + System.currentTimeMillis(), "write_file", "File written successfully: " + path);

        } catch (Exception e) {
            log.error("Write file failed", e);
            return ToolExecutionResult.failure("write-" + System.currentTimeMillis(), "write_file", "Error: " + e.getMessage());
        }
    }

    private ToolExecutionResult globFiles(String pattern) {
        try {
            log.info("Glob pattern: {}", pattern);
            StringBuilder result = new StringBuilder();

            // Simple glob implementation - search from current directory
            // In production, this should be more sophisticated
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            // Walk through the file tree looking for matches
            Path startDir = Paths.get(".").toAbsolutePath().normalize();
            Files.walk(startDir, 10)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    try {
                        return matcher.matches(p);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .limit(100) // Limit results
                .forEach(p -> result.append(p.toAbsolutePath().normalize()).append("\n"));

            String output = result.length() > 0 ? result.toString().trim() : "No files found matching pattern";
            return ToolExecutionResult.success("glob-" + System.currentTimeMillis(), "glob", output);

        } catch (Exception e) {
            log.error("Glob failed", e);
            return ToolExecutionResult.failure("glob-" + System.currentTimeMillis(), "glob", "Error: " + e.getMessage());
        }
    }

    private ToolExecutionResult grep(String pattern, String searchPath) {
        try {
            log.info("Grep pattern: {} in path: {}", pattern, searchPath);

            // Determine search directory
            Path baseDir = Paths.get(".").toAbsolutePath().normalize();
            if (searchPath != null && !searchPath.isEmpty()) {
                baseDir = Paths.get(searchPath).toAbsolutePath().normalize();
            }

            StringBuilder result = new StringBuilder();
            final String searchPattern = pattern;

            Files.walk(baseDir, 10)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString();
                    // Skip binary files and common ignored files
                    return !name.endsWith(".class") &&
                           !name.endsWith(".jar") &&
                           !name.startsWith(".");
                })
                .limit(50) // Limit file count
                .forEach(p -> {
                    try {
                        List<String> lines = Files.readAllLines(p);
                        for (int i = 0; i < lines.size(); i++) {
                            if (lines.get(i).contains(searchPattern)) {
                                result.append(p.toAbsolutePath().normalize())
                                      .append(":")
                                      .append(i + 1)
                                      .append(": ")
                                      .append(lines.get(i))
                                      .append("\n");
                            }
                        }
                    } catch (Exception e) {
                        // Skip files we can't read
                    }
                });

            String output = result.length() > 0 ? result.toString().trim() : "No matches found for pattern: " + pattern;
            return ToolExecutionResult.success("grep-" + System.currentTimeMillis(), "grep", output);

        } catch (Exception e) {
            log.error("Grep failed", e);
            return ToolExecutionResult.failure("grep-" + System.currentTimeMillis(), "grep", "Error: " + e.getMessage());
        }
    }

    /**
     * Interface for tool handlers.
     */
    public interface ToolHandler {
        ToolExecutionResult execute(ToolCall call, Map<String, Object> args);
        ToolDefinition getDefinition();
    }
}
