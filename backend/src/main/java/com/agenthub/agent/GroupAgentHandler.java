package com.agenthub.agent;

import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.agent.model.AgentTask;
import com.agenthub.model.entity.Agent;
import com.agenthub.model.entity.ConversationParticipant;
import com.agenthub.model.entity.Message;
import com.agenthub.model.dto.MessageVO;
import com.agenthub.model.enums.SenderType;
import com.agenthub.model.enums.MessageType;
import com.agenthub.repository.AgentMapper;
import com.agenthub.repository.ConversationParticipantMapper;
import com.agenthub.repository.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import com.agenthub.util.TriConsumer;

@Component
public class GroupAgentHandler {

    private static final Logger log = LoggerFactory.getLogger(GroupAgentHandler.class);

    private final Orchestrator orchestrator;
    private final AgentCore agentCore;
    private final AgentMapper agentMapper;
    private final ConversationParticipantMapper participantMapper;
    private final MessageMapper messageMapper;

    public GroupAgentHandler(Orchestrator orchestrator,
                            AgentCore agentCore,
                            AgentMapper agentMapper,
                            ConversationParticipantMapper participantMapper,
                            MessageMapper messageMapper) {
        this.orchestrator = orchestrator;
        this.agentCore = agentCore;
        this.agentMapper = agentMapper;
        this.participantMapper = participantMapper;
        this.messageMapper = messageMapper;
    }

    public AgentResponse handleGroupMessage(Message userMessage, Long conversationId) {
        log.info("Handling group message: id={}, conversationId={}",
            userMessage.getId(), conversationId);

        List<Agent> participants = getConversationAgents(conversationId);
        if (participants.isEmpty()) {
            log.warn("No agent participants found for conversation {}", conversationId);
            return createFallbackResponse("No agents available in this conversation.");
        }

        if (!orchestrator.shouldOrchestrate(userMessage)) {
            log.debug("No orchestration needed, using single agent");
            return handleSingleAgent(userMessage, participants);
        }

        List<AgentTask> tasks = orchestrator.decompose(userMessage.getContent(), participants);
        if (tasks.isEmpty()) {
            log.debug("No tasks from decomposition, using single agent");
            return handleSingleAgent(userMessage, participants);
        }

        log.info("Orchestrating {} tasks for message {}", tasks.size(), userMessage.getId());
        return orchestrateTasks(userMessage, tasks);
    }

    public void handleGroupMessageStreaming(Message userMessage, Long conversationId, Long userId,
            Map<Long, List<MessageVO>> pendingMessages,
            TriConsumer<Long, Long, String, Agent> sendStreamingUpdate,
            BiConsumer<Long, List<Long>> sendComplete) {

        log.info("Handling group message with streaming: id={}, conversationId={}",
            userMessage.getId(), conversationId);

        List<Agent> participants = getConversationAgents(conversationId);
        if (participants.isEmpty()) {
            log.warn("No agent participants found for conversation {}", conversationId);
            return;
        }

        List<Long> messageIds = new ArrayList<>();

        if (orchestrator.shouldOrchestrate(userMessage)) {
            // @mention mode: only call mentioned agents
            List<AgentTask> tasks = orchestrator.decompose(userMessage.getContent(), participants);
            if (tasks.isEmpty()) {
                // No valid mentions, fallback to all agents
                tasks = participants.stream().map(agent -> {
                    AgentTask task = new AgentTask();
                    task.setAgentId(agent.getId());
                    task.setTaskDescription(userMessage.getContent());
                    return task;
                }).collect(Collectors.toList());
            }

            for (AgentTask task : tasks) {
                Agent agent = agentMapper.selectById(task.getAgentId());
                if (agent == null || !agent.getEnabled()) {
                    log.warn("Agent {} not available", task.getAgentId());
                    continue;
                }
                String content = callAgentSync(userId, agent, task.getTaskDescription(), userMessage, messageIds, sendStreamingUpdate);
            }
        } else {
            // No @mention: call all agents sequentially
            for (Agent agent : participants) {
                String content = callAgentSync(userId, agent, userMessage.getContent(), userMessage, messageIds, sendStreamingUpdate);
            }
        }

        // Send completion event
        sendComplete.accept(userId, messageIds);
    }

    private String callAgentSync(Long userId, Agent agent, String content, Message parentMessage,
            List<Long> messageIds, TriConsumer<Long, Long, String, Agent> sendStreamingUpdate) {

        // Create placeholder message for this agent
        Message agentMessage = new Message();
        agentMessage.setConversationId(parentMessage.getConversationId());
        agentMessage.setSenderType(SenderType.AGENT.name());
        agentMessage.setSenderId(agent.getId());
        agentMessage.setContent("");
        agentMessage.setMessageType(MessageType.TEXT.name());
        agentMessage.setParentId(parentMessage.getId());
        messageMapper.insert(agentMessage);
        messageIds.add(agentMessage.getId());

        // Send initial streaming event
        sendStreamingUpdate.accept(userId, agentMessage.getId(), "", agent);

        // Build request
        AgentRequest request = new AgentRequest();
        request.setUserId(userId);
        request.setConversationId(parentMessage.getConversationId());
        request.setContent(content);
        request.setSystemPrompt(agent.getSystemPrompt());

        // Use streaming for real-time updates
        StringBuilder fullContent = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        Flux<String> stream = agentCore.generateStream(agent, request);

        stream.subscribe(
            chunk -> {
                if (chunk != null && !chunk.isEmpty()) {
                    fullContent.append(chunk);
                    // Update database
                    Message update = new Message();
                    update.setId(agentMessage.getId());
                    update.setContent(fullContent.toString());
                    messageMapper.updateById(update);
                    // Send streaming update
                    sendStreamingUpdate.accept(userId, agentMessage.getId(), fullContent.toString(), agent);
                }
            },
            error -> {
                log.error("Stream error for agent {}: {}", agent.getName(), error.getMessage());
                fullContent.append("\nStream error: ").append(error.getMessage());
                Message update = new Message();
                update.setId(agentMessage.getId());
                update.setContent(fullContent.toString());
                messageMapper.updateById(update);
                sendStreamingUpdate.accept(userId, agentMessage.getId(), fullContent.toString(), agent);
                latch.countDown();
            },
            () -> {
                // Stream complete
                log.debug("Stream complete for agent {}, content length: {}", agent.getName(), fullContent.length());
                latch.countDown();
            }
        );

        // Wait for stream to complete (with timeout)
        try {
            if (!latch.await(120, TimeUnit.SECONDS)) {
                log.warn("Stream timeout for agent {}", agent.getName());
                fullContent.append("\n[Timeout waiting for response]");
            }
        } catch (InterruptedException e) {
            log.error("Stream interrupted for agent {}", agent.getName());
            Thread.currentThread().interrupt();
        }

        return fullContent.toString();
    }

    private AgentResponse handleSingleAgent(Message userMessage, List<Agent> agents) {
        Agent agent = agents.get(0);
        AgentRequest request = buildAgentRequest(userMessage);
        request.setSystemPrompt(agent.getSystemPrompt());
        return agentCore.generate(agent, request);
    }

    private AgentResponse orchestrateTasks(Message userMessage, List<AgentTask> tasks) {
        List<AgentResponse> responses = new ArrayList<>();

        for (AgentTask task : tasks) {
            Agent agent = agentMapper.selectById(task.getAgentId());
            if (agent == null || !agent.getEnabled()) {
                log.warn("Agent {} not available", task.getAgentId());
                responses.add(createFallbackResponse("Agent not available"));
                continue;
            }

            AgentRequest request = buildAgentRequest(userMessage);
            request.setContent(task.getTaskDescription());
            request.setSystemPrompt(agent.getSystemPrompt());

            log.debug("Calling agent {} for task: {}", agent.getName(), task.getTaskDescription());
            AgentResponse response = agentCore.generate(agent, request);
            responses.add(response);
        }

        String aggregated = orchestrator.aggregate(responses);
        AgentResponse result = new AgentResponse();
        result.setContent(aggregated);
        result.setFinishReason("stop");

        if (responses.size() == 1 && !responses.get(0).getBlocks().isEmpty()) {
            responses.get(0).getBlocks().forEach(block ->
                result.addCodeBlock(block.getContent(), block.getLanguage()));
        }

        return result;
    }

    private AgentRequest buildAgentRequest(Message userMessage) {
        AgentRequest request = new AgentRequest();
        request.setUserId(userMessage.getSenderId());
        request.setConversationId(userMessage.getConversationId());
        request.setContent(userMessage.getContent());
        return request;
    }

    private List<Agent> getConversationAgents(Long conversationId) {
        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, conversationId)
            .isNotNull(ConversationParticipant::getAgentId);

        List<ConversationParticipant> participants = participantMapper.selectList(wrapper);

        return participants.stream()
            .map(p -> agentMapper.selectById(p.getAgentId()))
            .filter(a -> a != null && a.getEnabled())
            .collect(Collectors.toList());
    }

    private AgentResponse createFallbackResponse(String content) {
        AgentResponse response = new AgentResponse();
        response.setContent(content);
        response.setFinishReason("fallback");
        return response;
    }
}
