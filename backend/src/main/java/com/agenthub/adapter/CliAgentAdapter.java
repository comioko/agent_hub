package com.agenthub.adapter;

import com.agenthub.agent.AgentAdapter;
import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.agent.model.ProviderMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
public class CliAgentAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(CliAgentAdapter.class);
    private static final String DEFAULT_CLI_PATH = "claude";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String cliPath;

    public CliAgentAdapter() {
        this.cliPath = DEFAULT_CLI_PATH;
    }

    public CliAgentAdapter(String cliPath) {
        this.cliPath = cliPath != null ? cliPath : DEFAULT_CLI_PATH;
    }

    @Override
    public AgentResponse generate(AgentRequest request) {
        StringBuilder content = new StringBuilder();
        try {
            generateStream(request)
                .doOnNext(content::append)
                .doOnError(e -> log.error("CLI generate error", e))
                .blockLast();
        } catch (Exception e) {
            log.error("Error collecting CLI response", e);
        }
        AgentResponse response = new AgentResponse();
        response.setContent(content.toString());
        response.setFinishReason("stop");
        return response;
    }

    @Override
    public Flux<String> generateStream(AgentRequest request) {
        return Flux.create(emitter -> {
            new Thread(() -> {
                try {
                    // Use --print --output-format stream-json for simple streaming
                    ProcessBuilder pb = new ProcessBuilder(
                        cliPath,
                        "--print",
                        "--output-format", "stream-json",
                        "--verbose"
                    );
                    pb.environment().put("NO_COLOR", "1");
                    pb.redirectErrorStream(true);

                    Process process = pb.start();

                    BufferedWriter stdin = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader stdout = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

                    // Build the prompt
                    String prompt = buildPrompt(request);

                    // Send the prompt
                    stdin.write(prompt);
                    stdin.newLine();
                    stdin.flush();
                    stdin.close();

                    // Read streaming JSON responses
                    String line;
                    StringBuilder fullContent = new StringBuilder();

                    while ((line = stdout.readLine()) != null) {
                        String content = parseStreamLine(line);
                        if (content != null && !content.isEmpty()) {
                            fullContent.append(content);
                            emitter.next(content);
                        }
                    }

                    process.waitFor();
                    stdout.close();
                    emitter.complete();

                } catch (Exception e) {
                    log.error("Error in CLI generateStream", e);
                    emitter.error(e);
                }
            }).start();
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private String buildPrompt(AgentRequest request) {
        StringBuilder sb = new StringBuilder();

        // Add system prompt if present
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            sb.append(request.getSystemPrompt()).append("\n\n");
        }

        // Add conversation history if present
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            sb.append("Conversation history:\n");
            for (var msg : request.getHistory()) {
                String role = msg.getSenderType() != null && "AGENT".equals(msg.getSenderType()) ? "Assistant" : "User";
                sb.append(role).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        // Add current message
        if (request.getContent() != null) {
            sb.append(request.getContent());
        }

        return sb.toString();
    }

    private String parseStreamLine(String line) {
        try {
            // Try to parse as JSON
            JsonNode root = objectMapper.readTree(line);
            String type = root.path("type").asText(null);

            if ("result".equals(type)) {
                // Simple result format
                return root.path("result").asText("");
            }

            if ("assistant".equals(type)) {
                // Assistant message format
                JsonNode message = root.path("message");
                JsonNode content = message.path("content");
                if (content.isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode block : content) {
                        if ("text".equals(block.path("type").asText())) {
                            sb.append(block.path("text").asText());
                        }
                    }
                    return sb.toString();
                }
            }

            // Skip system/hook events
            if ("system".equals(type)) {
                return "";
            }

            // Skip other events
            return "";

        } catch (Exception e) {
            // Not JSON - might be plain text or debug output
            if (line.trim().startsWith("[") || line.trim().startsWith("{")) {
                return ""; // Skip JSON-like lines
            }
            // Return as plain text
            return line;
        }
    }

    @Override
    public ProviderMeta getProviderMeta() {
        String name = cliPath.contains("claude") ? "Claude Code CLI" : "CLI Agent";
        return new ProviderMeta("CLI", name, cliPath, isAvailable());
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(cliPath, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("CLI not available: {}", e.getMessage());
            return false;
        }
    }

    public String getCliPath() {
        return cliPath;
    }
}
