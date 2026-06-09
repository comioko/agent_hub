package com.agenthub.service;

import com.agenthub.model.dto.CodeExecutionResult;
import com.agenthub.model.entity.MessageBlock;
import com.agenthub.repository.MessageBlockMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeBlockService {

    private final MessageBlockMapper messageBlockMapper;

    public CodeBlockService(MessageBlockMapper messageBlockMapper) {
        this.messageBlockMapper = messageBlockMapper;
    }

    public MessageBlock updateCodeBlock(Long blockId, String content) {
        MessageBlock block = messageBlockMapper.selectById(blockId);
        if (block == null) {
            throw new RuntimeException("Block not found");
        }

        block.setContent(content);
        messageBlockMapper.updateById(block);
        return block;
    }

    public CodeExecutionResult executeCode(MessageBlock block) {
        String language = block.getLanguage() != null ? block.getLanguage().toLowerCase() : "";
        String code = block.getContent();

        CodeExecutionResult result = new CodeExecutionResult();

        try {
            switch (language) {
                case "javascript":
                case "js":
                    result = executeJavaScript(code);
                    break;
                case "python":
                case "py":
                    result = executePython(code);
                    break;
                case "java":
                    result = executeJava(code);
                    break;
                case "bash":
                case "shell":
                    result = executeBash(code);
                    break;
                default:
                    result.setSuccess(false);
                    result.setOutput("Unsupported language: " + language + "\nSupported languages: javascript, python, java, bash");
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setOutput("Execution error: " + e.getMessage());
        }

        return result;
    }

    private CodeExecutionResult executeJavaScript(String code) throws Exception {
        // Use Node.js if available, otherwise use Nashorn (Java built-in)
        String output;
        boolean success = true;

        // Try Node.js first
        try {
            Process process = new ProcessBuilder("node", "-e", code)
                    .redirectErrorStream(true)
                    .start();
            output = readProcessOutput(process);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                success = false;
            }
        } catch (IOException e) {
            // Node not available, try Java's Nashorn engine
            try {
                // Use a simple approach with jshell fallback
                Process process = new ProcessBuilder("jshell", "-s", "-")
                        .redirectErrorStream(true)
                        .start();

                // Write code to jshell
                try (OutputStream os = process.getOutputStream()) {
                    os.write(code.getBytes());
                    os.write("\n/exit\n".getBytes());
                    os.flush();
                }

                output = readProcessOutput(process);
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    success = false;
                }
            } catch (IOException ex) {
                output = "JavaScript execution requires Node.js or jshell to be installed.\n" +
                        "Please install Node.js for JavaScript support.\n" +
                        "Error: " + e.getMessage();
                success = false;
            }
        }

        CodeExecutionResult result = new CodeExecutionResult();
        result.setSuccess(success);
        result.setOutput(output);
        return result;
    }

    private CodeExecutionResult executePython(String code) throws Exception {
        String output = "";
        boolean success = true;

        // Try python3 first, then python
        String[] commands = {"python3", "python"};
        Exception lastException = null;

        for (String pythonCmd : commands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(pythonCmd, "-c", code);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                output = readProcessOutput(process);
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    success = true;
                    break;
                } else {
                    success = false;
                }
            } catch (IOException e) {
                lastException = e;
                continue;
            }
        }

        if (lastException != null && !success) {
            output = "Python execution failed. Please install Python 3.\n" +
                    "Error: " + lastException.getMessage();
            success = false;
        }

        CodeExecutionResult result = new CodeExecutionResult();
        result.setSuccess(success);
        result.setOutput(output);
        return result;
    }

    private CodeExecutionResult executeJava(String code) throws Exception {
        // Extract class name from code
        Pattern classPattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = classPattern.matcher(code);

        if (!matcher.find()) {
            CodeExecutionResult result = new CodeExecutionResult();
            result.setSuccess(false);
            result.setOutput("Java code must contain a public class with a main method.\n" +
                    "Example:\npublic class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}");
            return result;
        }

        String className = matcher.group(1);
        String tempDir = System.getProperty("java.io.tmpdir");
        String fileName = className + ".java";
        File tempFile = new File(tempDir, fileName);

        // Write code to file
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(code);
        }

        // Compile
        Process compileProcess = new ProcessBuilder("javac", fileName)
                .directory(new File(tempDir))
                .redirectErrorStream(true)
                .start();

        String compileOutput = readProcessOutput(compileProcess);
        int compileExitCode = compileProcess.waitFor();

        if (compileExitCode != 0) {
            tempFile.delete();
            CodeExecutionResult result = new CodeExecutionResult();
            result.setSuccess(false);
            result.setOutput("Compilation error:\n" + compileOutput);
            return result;
        }

        // Run
        Process runProcess = new ProcessBuilder("java", "-cp", tempDir, className)
                .directory(new File(tempDir))
                .redirectErrorStream(true)
                .start();

        String runOutput = readProcessOutput(runProcess);
        int runExitCode = runProcess.waitFor();

        // Clean up
        tempFile.delete();
        new File(tempDir, className + ".class").delete();

        CodeExecutionResult result = new CodeExecutionResult();
        result.setSuccess(runExitCode == 0);
        result.setOutput(runOutput);
        return result;
    }

    private CodeExecutionResult executeBash(String code) throws Exception {
        Process process = new ProcessBuilder("bash", "-c", code)
                .redirectErrorStream(true)
                .start();

        String output = readProcessOutput(process);
        int exitCode = process.waitFor();

        CodeExecutionResult result = new CodeExecutionResult();
        result.setSuccess(exitCode == 0);
        result.setOutput(output);
        return result;
    }

    private String readProcessOutput(Process process) throws Exception {
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        return output.toString().trim();
    }
}
