package com.agenthub.agent;

import com.agenthub.agent.model.*;
import com.agenthub.model.entity.Agent;
import com.agenthub.model.entity.AgentSession;
import com.agenthub.service.AgentSessionService;
import com.agenthub.service.ToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core agent loop that handles multi-turn task execution with tool calls.
 * Implements the ReAct pattern: Reason -> Act -> Observe
 */
@Component
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);
    private static final int MAX_ITERATIONS = 50;
    private static final long SSE_TIMEOUT = Long.MAX_VALUE;

    private final AgentCore agentCore;
    private final ToolExecutor toolExecutor;
    private final AgentSessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Active emitters for SSE streaming
    private final Map<String, SseEmitter> sessionEmitters = new ConcurrentHashMap<>();

    public AgentLoop(AgentCore agentCore, ToolExecutor toolExecutor, AgentSessionService sessionService) {
        this.agentCore = agentCore;
        this.toolExecutor = toolExecutor;
        this.sessionService = sessionService;
    }

    /**
     * Start executing a task in a session.
     */
    public void startExecution(String sessionId, Agent agent, String task) {
        SseEmitter emitter = sessionEmitters.get(sessionId);
        if (emitter == null) {
            emitter = new SseEmitter(SSE_TIMEOUT);
            sessionEmitters.put(sessionId, emitter);

            emitter.onCompletion(() -> sessionEmitters.remove(sessionId));
            emitter.onTimeout(() -> sessionEmitters.remove(sessionId));
            emitter.onError(e -> sessionEmitters.remove(sessionId));
        }

        // Run execution in background
        final SseEmitter emitterRef = emitter;
        new Thread(() -> {
            try {
                executeLoop(sessionId, agent, task, emitterRef);
            } catch (Exception e) {
                log.error("Error in agent loop for session: {}", sessionId, e);
                sessionService.setError(sessionId, e.getMessage());
                sendError(sessionId, emitterRef, e.getMessage());
            }
        }).start();
    }

    /**
     * Main agent loop execution.
     */
    private void executeLoop(String sessionId, Agent agent, String task, SseEmitter emitter) {
        log.info("Starting agent loop for session: {}", sessionId);

        List<AgentSession.AgentMessage> messages = new ArrayList<>();
        Map<String, ToolExecutionResult> toolResults = new HashMap<>();
        int iteration = 0;

        // Build initial user message
        AgentSession.AgentMessage userMessage = new AgentSession.AgentMessage();
        userMessage.setRole("user");
        userMessage.setContent(task);
        messages.add(userMessage);

        try {
            // Send initial status
            sendEvent(sessionId, emitter, "status", Map.of(
                "status", "RUNNING",
                "message", "Starting task execution..."
            ));

            while (iteration < MAX_ITERATIONS) {
                // Check if we should continue
                if (!sessionService.shouldContinue(sessionId)) {
                    log.info("Session {} interrupted by user", sessionId);
                    sendEvent(sessionId, emitter, "status", Map.of(
                        "status", "CANCELLED",
                        "message", "Task cancelled by user"
                    ));
                    sessionService.updateStatus(sessionId, "CANCELLED");
                    return;
                }

                iteration++;
                sessionService.incrementIteration(sessionId);

                log.debug("Iteration {} for session {}", iteration, sessionId);

                // Build request with tools
                AgentRequest request = buildRequest(agent, messages, toolResults);

                // Send thinking status
                sendEvent(sessionId, emitter, "status", Map.of(
                    "status", "THINKING",
                    "message", "Agent is thinking...",
                    "iteration", iteration
                ));

                // Call the LLM
                AgentResponse response = agentCore.generate(agent, request);

                // Process response
                if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                    // Agent requested tool calls
                    sessionService.updateStatus(sessionId, "WAITING_TOOL");

                    for (ToolCall toolCall : response.getToolCalls()) {
                        // Add assistant message with tool call to history
                        AgentSession.AgentMessage assistantMsg = new AgentSession.AgentMessage();
                        assistantMsg.setRole("assistant");
                        assistantMsg.setContent(response.getContent());
                        assistantMsg.setToolCallId(toolCall.getId());
                        messages.add(assistantMsg);

                        // Send tool call event
                        sendEvent(sessionId, emitter, "tool_call", Map.of(
                            "toolCallId", toolCall.getId(),
                            "toolName", toolCall.getFunction().getName(),
                            "arguments", toolCall.getArgumentsMap()
                        ));

                        // Execute tool
                        ToolExecutionResult result = toolExecutor.execute(toolCall);
                        toolResults.put(toolCall.getId(), result);

                        // Add tool result to messages
                        AgentSession.AgentMessage toolResultMsg = new AgentSession.AgentMessage();
                        toolResultMsg.setRole("tool");
                        toolResultMsg.setContent(result.getResult());
                        toolResultMsg.setToolCallId(toolCall.getId());
                        messages.add(toolResultMsg);

                        // Send tool result event
                        sendEvent(sessionId, emitter, "tool_result", Map.of(
                            "toolCallId", toolCall.getId(),
                            "toolName", toolCall.getFunction().getName(),
                            "result", result.getResult(),
                            "success", result.isSuccess()
                        ));

                        // Save to session
                        sessionService.addMessage(sessionId, assistantMsg);
                        sessionService.addToolResult(sessionId, new AgentSession.ToolResult(
                            toolCall.getId(),
                            toolCall.getFunction().getName(),
                            result.getResult()
                        ));
                    }

                    sessionService.updateStatus(sessionId, "RUNNING");

                } else {
                    // Final response - no more tool calls
                    AgentSession.AgentMessage finalMsg = new AgentSession.AgentMessage();
                    finalMsg.setRole("assistant");
                    finalMsg.setContent(response.getContent());
                    messages.add(finalMsg);
                    sessionService.addMessage(sessionId, finalMsg);

                    // Send final response
                    sendEvent(sessionId, emitter, "complete", Map.of(
                        "status", "COMPLETED",
                        "content", response.getContent(),
                        "iterations", iteration
                    ));

                    sessionService.updateStatus(sessionId, "COMPLETED");
                    log.info("Session {} completed after {} iterations", sessionId, iteration);
                    return;
                }
            }

            // Max iterations reached
            sendEvent(sessionId, emitter, "error", Map.of(
                "status", "FAILED",
                "message", "Maximum iterations (" + MAX_ITERATIONS + ") reached"
            ));
            sessionService.setError(sessionId, "Maximum iterations reached");

        } catch (Exception e) {
            log.error("Error in agent loop", e);
            sendError(sessionId, emitter, e.getMessage());
            sessionService.setError(sessionId, e.getMessage());
        }
    }

    /**
     * Build agent request with messages and tools.
     */
    private AgentRequest buildRequest(Agent agent, List<AgentSession.AgentMessage> messages,
                                       Map<String, ToolExecutionResult> toolResults) {
        AgentRequest request = new AgentRequest();
        request.setUserId(agent.getOwnerId());
        request.setContent(messages.isEmpty() ? "" : messages.get(messages.size() - 1).getContent());
        request.setSystemPrompt(buildSystemPrompt(agent));

        // Convert messages to Message format for history
        List<com.agenthub.model.entity.Message> history = new ArrayList<>();
        for (AgentSession.AgentMessage msg : messages) {
            if (msg.getRole().equals("user")) {
                com.agenthub.model.entity.Message m = new com.agenthub.model.entity.Message();
                m.setSenderType("USER");
                m.setContent(msg.getContent());
                history.add(m);
            } else if (msg.getRole().equals("assistant") && msg.getToolCallId() == null) {
                com.agenthub.model.entity.Message m = new com.agenthub.model.entity.Message();
                m.setSenderType("AGENT");
                m.setContent(msg.getContent());
                history.add(m);
            }
        }
        request.setHistory(history);

        // Add tools
        request.setTools(toolExecutor.getAvailableTools());

        return request;
    }

    /**
     * Build system prompt for the agent.
     */
    private String buildSystemPrompt(Agent agent) {
        StringBuilder prompt = new StringBuilder();

        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isEmpty()) {
            prompt.append(agent.getSystemPrompt()).append("\n\n");
        }

        prompt.append("""
            You are an AI assistant with access to various tools.
            Available tools: bash, read_file, write_file, glob, grep

            Guidelines:
            - Use tools when needed to accomplish the user's task
            - Always verify your actions before making changes
            - Provide clear explanations of what you're doing
            - If a tool fails, explain the error and suggest alternatives

            When using tools:
            1. Make one tool call at a time
            2. Wait for the result before making the next call
            3. Use the tool results to inform your next steps
            """);

        return prompt.toString();
    }

    /**
     * Subscribe to session events.
     */
    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        sessionEmitters.put(sessionId, emitter);

        emitter.onCompletion(() -> sessionEmitters.remove(sessionId));
        emitter.onTimeout(() -> sessionEmitters.remove(sessionId));
        emitter.onError(e -> sessionEmitters.remove(sessionId));

        try {
            emitter.send(SseEmitter.event().name("connected").data("Connected to session: " + sessionId));
        } catch (IOException e) {
            emitter.complete();
            sessionEmitters.remove(sessionId);
        }

        return emitter;
    }

    /**
     * Request interruption of a session.
     */
    public boolean interrupt(String sessionId) {
        return sessionService.requestInterruption(sessionId);
    }

    /**
     * Send SSE event.
     */
    private void sendEvent(String sessionId, SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data));
        } catch (IOException e) {
            log.error("Failed to send SSE event for session: {}", sessionId, e);
            emitter.complete();
            sessionEmitters.remove(sessionId);
        }
    }

    /**
     * Send error event.
     */
    private void sendError(String sessionId, SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                .name("error")
                .data(Map.of(
                    "status", "FAILED",
                    "message", message
                )));
            emitter.complete();
        } catch (IOException e) {
            log.error("Failed to send error for session: {}", sessionId, e);
        }
        sessionEmitters.remove(sessionId);
    }

    /**
     * Clean up resources for a session.
     */
    public void cleanup(String sessionId) {
        sessionEmitters.remove(sessionId);
    }
}
