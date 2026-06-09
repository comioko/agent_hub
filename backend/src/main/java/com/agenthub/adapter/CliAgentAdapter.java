package com.agenthub.adapter;

import com.agenthub.agent.AgentAdapter;
import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.agent.model.ProviderMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CliAgentAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(CliAgentAdapter.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String DEFAULT_CLI_PATH = "claude";

    private final String cliPath;
    private final ObjectMapper objectMapper;
    private final Map<String, CliSession> sessions;
    private final AtomicInteger sessionCounter;

    public CliAgentAdapter() {
        this.cliPath = DEFAULT_CLI_PATH;
        this.objectMapper = new ObjectMapper();
        this.sessions = new ConcurrentHashMap<>();
        this.sessionCounter = new AtomicInteger(0);
    }

    public CliAgentAdapter(String cliPath) {
        this.cliPath = cliPath != null ? cliPath : DEFAULT_CLI_PATH;
        this.objectMapper = new ObjectMapper();
        this.sessions = new ConcurrentHashMap<>();
        this.sessionCounter = new AtomicInteger(0);
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
        String sessionId = getOrCreateSession();

        return Flux.create(emitter -> {
            try {
                CliSession session = sessions.get(sessionId);
                if (session == null || !session.process.isAlive()) {
                    session = createSession(sessionId);
                    sessions.put(sessionId, session);
                }

                String response = sendMessage(session, request);
                parseAndEmit(response, emitter);

                emitter.complete();
            } catch (Exception e) {
                log.error("Error in generateStream", e);
                emitter.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    public ProviderMeta getProviderMeta() {
        String name = cliPath.contains("openclaw") ? "OpenClaw CLI" : "Claude Code CLI";
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

    private String getOrCreateSession() {
        return cliPath + "-" + sessionCounter.incrementAndGet();
    }

    private CliSession createSession(String sessionId) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cliPath);
        pb.environment().put("NO_COLOR", "1");
        pb.environment().put("CLAUDE_FORMAT", "json");
        pb.redirectErrorStream(true);

        Process process = pb.start();

        CliSession session = new CliSession();
        session.process = process;
        session.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        session.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        session.rpcMapper = objectMapper;
        session.messageId = new AtomicInteger(1);

        initializeSession(session);
        startDrainThread(session);

        return session;
    }

    private void initializeSession(CliSession session) throws IOException {
        ObjectNode initializeParams = objectMapper.createObjectNode();
        initializeParams.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode roots = objectMapper.createObjectNode();
        roots.put("listChanged", true);
        capabilities.set("roots", roots);
        capabilities.put("sampling", objectMapper.createObjectNode());
        initializeParams.set("capabilities", capabilities);

        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "agenthub");
        clientInfo.put("version", "1.0.0");
        initializeParams.set("clientInfo", clientInfo);

        sendJsonRpc(session, "initialize", initializeParams, 1);
        readJsonResponse(session);

        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        session.stdin.write(objectMapper.writeValueAsString(notification));
        session.stdin.newLine();
        session.stdin.flush();
    }

    private String sendMessage(CliSession session, AgentRequest request) throws IOException {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("systemPrompt", request.getSystemPrompt() != null ? request.getSystemPrompt() : "");

        String userMessage = buildUserMessage(request);
        ObjectNode messageContent = objectMapper.createObjectNode();
        messageContent.put("type", "text");
        messageContent.put("text", userMessage);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.set("content", messageContent);

        params.put("maxTokens", 4096);

        var messagesArray = objectMapper.createArrayNode();
        messagesArray.add(userMsg);
        params.set("messages", messagesArray);

        int msgId = session.messageId.incrementAndGet();
        sendJsonRpc(session, "sampling/createMessage", params, msgId);

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = session.stdout.readLine()) != null) {
            response.append(line);
            if (isCompleteJson(line)) {
                break;
            }
        }
        return response.toString();
    }

    private String buildUserMessage(AgentRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getContent() != null) {
            sb.append(request.getContent());
        }
        return sb.toString();
    }

    private void parseAndEmit(String jsonResponse, FluxSink<String> emitter) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode result = root.get("result");
            if (result != null) {
                JsonNode content = result.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode item : content) {
                        if ("text".equals(item.path("type").asText())) {
                            String text = item.path("text").asText();
                            emitter.next(text);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing CLI response: {}", jsonResponse, e);
        }
    }

    private void sendJsonRpc(CliSession session, String method, ObjectNode params, int id) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);

        String json = objectMapper.writeValueAsString(request);
        session.stdin.write(json);
        session.stdin.newLine();
        session.stdin.flush();
        log.debug("Sent JSON-RPC: {}", json);
    }

    private String readJsonResponse(CliSession session) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = session.stdout.readLine()) != null) {
            sb.append(line);
            if (isCompleteJson(line)) {
                break;
            }
        }
        String response = sb.toString();
        log.debug("Received JSON-RPC response: {}", response);
        return response;
    }

    private boolean isCompleteJson(String line) {
        try {
            objectMapper.readTree(line);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void startDrainThread(CliSession session) {
        Thread drainThread = new Thread(() -> {
            try {
                String line;
                while ((line = session.stdout.readLine()) != null) {
                    if (isCompleteJson(line)) {
                        log.trace("Drained JSON: {}", line);
                    } else {
                        log.trace("Drained non-JSON: {}", line);
                    }
                }
            } catch (IOException e) {
                log.debug("Drain thread ended");
            }
        }, "cli-drain-" + session.process.hashCode());
        drainThread.setDaemon(true);
        drainThread.start();
    }

    private static class CliSession {
        Process process;
        BufferedWriter stdin;
        BufferedReader stdout;
        ObjectMapper rpcMapper;
        AtomicInteger messageId;
    }
}
