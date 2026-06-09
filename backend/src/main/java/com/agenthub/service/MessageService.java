package com.agenthub.service;

import com.agenthub.agent.AgentCore;
import com.agenthub.agent.AgentAdapter;
import com.agenthub.agent.GroupAgentHandler;
import com.agenthub.agent.Orchestrator;
import com.agenthub.agent.model.*;
import com.agenthub.exception.BusinessException;
import com.agenthub.model.dto.MessageVO;
import com.agenthub.model.dto.SendMessageRequest;
import com.agenthub.model.entity.*;
import com.agenthub.model.enums.MessageType;
import com.agenthub.model.enums.SenderType;
import com.agenthub.repository.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.agenthub.service.cache.SessionCacheService;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int MAX_TOOL_CALLS = 10;

    private final MessageMapper messageMapper;
    private final MessageBlockMapper messageBlockMapper;
    private final MessageVersionMapper messageVersionMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationParticipantMapper participantMapper;
    private final AgentMapper agentMapper;
    private final AgentCore agentCore;
    private final SessionCacheService sessionCacheService;
    private final ToolExecutor toolExecutor;
    private final Orchestrator orchestrator;
    private final GroupAgentHandler groupAgentHandler;

    private final Map<Long, SseEmitter> activeEmitters = new ConcurrentHashMap<>();
    private final Map<Long, List<MessageVO>> pendingAgentMessages = new ConcurrentHashMap<>();

    public MessageService(MessageMapper messageMapper,
                         MessageBlockMapper messageBlockMapper,
                         MessageVersionMapper messageVersionMapper,
                         ConversationMapper conversationMapper,
                         ConversationParticipantMapper participantMapper,
                         AgentMapper agentMapper,
                         AgentCore agentCore,
                         @Autowired(required = false) SessionCacheService sessionCacheService,
                         @Autowired(required = false) ToolExecutor toolExecutor,
                         Orchestrator orchestrator,
                         GroupAgentHandler groupAgentHandler) {
        this.messageMapper = messageMapper;
        this.messageBlockMapper = messageBlockMapper;
        this.messageVersionMapper = messageVersionMapper;
        this.conversationMapper = conversationMapper;
        this.participantMapper = participantMapper;
        this.agentMapper = agentMapper;
        this.agentCore = agentCore;
        this.sessionCacheService = sessionCacheService;
        this.toolExecutor = toolExecutor;
        this.orchestrator = orchestrator;
        this.groupAgentHandler = groupAgentHandler;
    }

    public MessageVO sendMessage(SendMessageRequest request, User sender) {
        Conversation conversation = conversationMapper.selectById(request.getConversationId());
        if (conversation == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, request.getConversationId())
            .eq(ConversationParticipant::getUserId, sender.getId());
        if (participantMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // Save user message
        Message userMessage = new Message();
        userMessage.setConversationId(request.getConversationId());
        userMessage.setSenderType(SenderType.USER.name());
        userMessage.setSenderId(sender.getId());
        userMessage.setContent(request.getContent());
        userMessage.setMessageType(MessageType.TEXT.name());
        userMessage.setParentId(request.getParentId());
        messageMapper.insert(userMessage);

        updateConversationTimestampAndTitle(request.getConversationId(), request.getContent());

        // Invalidate session cache when new message is sent
        if (sessionCacheService != null) {
            sessionCacheService.invalidateMessages(request.getConversationId());
        }

        // Get all agent participants for this conversation
        LambdaQueryWrapper<ConversationParticipant> agentWrapper = new LambdaQueryWrapper<>();
        agentWrapper.eq(ConversationParticipant::getConversationId, request.getConversationId())
            .isNotNull(ConversationParticipant::getAgentId);
        List<ConversationParticipant> agentParticipants = participantMapper.selectList(agentWrapper);

        final Long userId = sender.getId();
        final Long conversationId = request.getConversationId();

        // Check if this is a group chat (multiple agents)
        boolean isGroupChat = agentParticipants.size() > 1;

        if (isGroupChat) {
            // Group chat: use GroupAgentHandler for multi-agent orchestration
            final Message finalUserMessage = userMessage;
            pendingAgentMessages.put(userId, new ArrayList<>());

            // Send initial event to indicate group chat started
            sendGroupChatStartEvent(userId);

            // Handle group message asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    groupAgentHandler.handleGroupMessageStreaming(
                        finalUserMessage, conversationId, userId, pendingAgentMessages,
                        this::sendStreamingUpdate, this::sendGroupChatCompleteEvent);
                } catch (Exception e) {
                    log.error("Error handling group message", e);
                    sendGroupChatErrorEvent(userId, e.getMessage());
                }
            });
        } else {
            // Single chat: original logic
            ConversationParticipant agentParticipant = agentParticipants.isEmpty() ? null : agentParticipants.get(0);

            Message agentMessage = new Message();
            agentMessage.setConversationId(request.getConversationId());
            agentMessage.setSenderType(SenderType.AGENT.name());
            agentMessage.setSenderId(agentParticipant != null ? agentParticipant.getAgentId() : 0L);
            agentMessage.setContent("");
            agentMessage.setMessageType(MessageType.TEXT.name());
            agentMessage.setParentId(userMessage.getId());
            messageMapper.insert(agentMessage);

            // Build agent message VO for streaming updates
            MessageVO agentMessageVO = buildMessageVO(agentMessage);
            pendingAgentMessages.put(userId, Collections.singletonList(agentMessageVO));

            final Agent agent = agentParticipant != null ? agentMapper.selectById(agentParticipant.getAgentId()) : null;

            // Send initial streaming event to notify frontend about new agent message
            sendStreamingUpdate(userId, agentMessage.getId(), "", agent);

            if (agent != null && agent.getEnabled()) {
                List<Message> history = getConversationHistory(conversationId, 20);

                AgentRequest agentRequest = new AgentRequest();
                agentRequest.setUserId(userId);
                agentRequest.setConversationId(conversationId);
                agentRequest.setContent(request.getContent());
                agentRequest.setSystemPrompt(agent.getSystemPrompt());
                agentRequest.setHistory(history);

                // Stream response asynchronously
                final Message finalAgentMessage = agentMessage;
                streamAgentResponse(userId, agent, agentRequest, finalAgentMessage);
            }
        }

        return buildMessageVO(userMessage);
    }

    private void streamAgentResponse(Long userId, Agent agent, AgentRequest request, Message agentMessage) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) {
            log.warn("No SSE emitter for user {}, skipping stream", userId);
            return;
        }

        try {
            AgentAdapter adapter = agentCore.getAdapter(agent.getProvider().toUpperCase());
            if (adapter == null) {
                adapter = agentCore.getAdapter("BUILTIN");
            }

            // Add available tools to the request
            if (toolExecutor != null) {
                List<ToolDefinition> tools = toolExecutor.getAvailableTools();
                request.setTools(tools);
                log.debug("Added {} tools to request", tools.size());
            }

            // Process with tool loop (max 10 iterations)
            processWithToolLoop(userId, agent, request, agentMessage, adapter, emitter);

        } catch (Exception e) {
            log.error("Failed to stream agent response", e);
            completeAgentMessage(userId, agentMessage.getId(), "Agent error: " + e.getMessage(), null);
        }
    }

    private void processWithToolLoop(Long userId, Agent agent, AgentRequest request,
                                     Message agentMessage, AgentAdapter adapter, SseEmitter emitter) {
        StringBuilder fullContent = new StringBuilder();
        List<ToolExecutionResult> toolResults = new ArrayList<>();
        int iterationCount = 0;

        try {
            do {
                iterationCount++;
                log.debug("Tool loop iteration {}", iterationCount);

                // Check for tool_calls in non-streaming mode first
                AgentResponse response = adapter.generate(request);

                if (response.isHasToolCalls() && response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                    log.info("Detected {} tool calls", response.getToolCalls().size());

                    // Send tool call notifications
                    for (ToolCall toolCall : response.getToolCalls()) {
                        sendToolCallEvent(userId, agentMessage.getId(), toolCall, agent);
                    }

                    // Execute tools and collect results
                    toolResults.clear();
                    for (ToolCall toolCall : response.getToolCalls()) {
                        if (toolExecutor != null) {
                            ToolExecutionResult result = toolExecutor.execute(toolCall);
                            toolResults.add(result);
                            sendToolResultEvent(userId, agentMessage.getId(), result, agent);
                            log.info("Tool {} executed: {}", toolCall.getFunction().getName(),
                                    result.isSuccess() ? "success" : "failure");
                        } else {
                            // No tool executor available
                            ToolExecutionResult result = ToolExecutionResult.failure(
                                    toolCall.getId(), toolCall.getFunction().getName(),
                                    "Tool executor not available");
                            toolResults.add(result);
                            sendToolResultEvent(userId, agentMessage.getId(), result, agent);
                        }
                    }

                    // Add tool results to request for next iteration
                    request.setToolResults(new ArrayList<>(toolResults));
                    request.setContent(""); // Clear content, using tool results instead

                    // Append tool call content if any
                    if (response.getContent() != null && !response.getContent().isEmpty()) {
                        fullContent.append(response.getContent());
                        updateAgentMessage(userId, agentMessage.getId(), fullContent.toString(), agent.getId());
                        sendStreamingUpdate(userId, agentMessage.getId(), fullContent.toString(), agent);
                    }
                } else {
                    // No tool calls, this is the final response - use streaming
                    if (response.getContent() != null) {
                        fullContent.append(response.getContent());
                    }
                    // Use streaming for the final response
                    processFinalResponseWithStreaming(userId, agent, request, agentMessage, fullContent.toString());
                    return;
                }

            } while (iterationCount < MAX_TOOL_CALLS);

            // Max iterations reached
            log.warn("Max tool call iterations ({}) reached", MAX_TOOL_CALLS);
            String maxIterationsMsg = fullContent.toString() +
                "\n\n[Maximum tool call iterations reached. Please try a simpler request.]";
            updateAgentMessage(userId, agentMessage.getId(), maxIterationsMsg, agent.getId());
            sendStreamingUpdate(userId, agentMessage.getId(), maxIterationsMsg, agent);
            completeAgentMessage(userId, agentMessage.getId(), maxIterationsMsg, null);

        } catch (Exception e) {
            log.error("Tool loop error for user {}", userId, e);
            String errorMsg = fullContent.toString() + "\n\nError: " + e.getMessage();
            updateAgentMessage(userId, agentMessage.getId(), errorMsg, agent.getId());
            sendStreamingUpdate(userId, agentMessage.getId(), errorMsg, agent);
            completeAgentMessage(userId, agentMessage.getId(), errorMsg, null);
        }
    }

    private void processFinalResponseWithStreaming(Long userId, Agent agent, AgentRequest request,
                                                   Message agentMessage, String initialContent) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) return;

        StringBuilder fullContent = new StringBuilder(initialContent != null ? initialContent : "");

        // If we already have full content (from non-streaming generate), just send it
        if (fullContent.length() > 0) {
            updateAgentMessage(userId, agentMessage.getId(), fullContent.toString(), agent.getId());
            sendStreamingUpdate(userId, agentMessage.getId(), fullContent.toString(), agent);
            completeAgentMessage(userId, agentMessage.getId(), fullContent.toString(), null);
            return;
        }

        // Use streaming from agentCore for fresh content
        Flux<String> stream = agentCore.generateStream(agent, request);

        stream.subscribe(
            chunk -> {
                if (chunk != null && !chunk.isEmpty()) {
                    fullContent.append(chunk);
                    updateAgentMessage(userId, agentMessage.getId(), fullContent.toString(), agent.getId());
                    sendStreamingUpdate(userId, agentMessage.getId(), fullContent.toString(), agent);
                }
            },
            error -> {
                log.error("Stream error for user {}", userId, error);
                String errorMsg = fullContent.toString() + "\n\nStream error: " + error.getMessage();
                updateAgentMessage(userId, agentMessage.getId(), errorMsg, agent.getId());
                sendStreamingUpdate(userId, agentMessage.getId(), errorMsg, agent);
                completeAgentMessage(userId, agentMessage.getId(), errorMsg, null);
            },
            () -> {
                // Stream complete
                log.debug("Stream complete for user {}, total content length: {}", userId, fullContent.length());
                completeAgentMessage(userId, agentMessage.getId(), fullContent.toString(), null);
            }
        );
    }

    private void sendToolCallEvent(Long userId, Long messageId, ToolCall toolCall, Agent agent) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) return;

        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("messageId", messageId);
            eventData.put("callId", toolCall.getId());
            eventData.put("tool", toolCall.getFunction().getName());
            eventData.put("arguments", toolCall.getArgumentsMap());

            emitter.send(SseEmitter.event()
                .name("tool_call")
                .data(eventData));
        } catch (IOException e) {
            log.error("Failed to send tool call event", e);
        }
    }

    private void sendToolResultEvent(Long userId, Long messageId, ToolExecutionResult result, Agent agent) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) return;

        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("messageId", messageId);
            eventData.put("callId", result.getCallId());
            eventData.put("tool", result.getToolName());
            eventData.put("success", result.isSuccess());
            eventData.put("result", result.getResult());
            if (result.getError() != null) {
                eventData.put("error", result.getError());
            }

            emitter.send(SseEmitter.event()
                .name("tool_result")
                .data(eventData));
        } catch (IOException e) {
            log.error("Failed to send tool result event", e);
        }
    }

    private void sendGroupChatStartEvent(Long userId) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) return;
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("type", "group_start");
            emitter.send(SseEmitter.event()
                .name("group_start")
                .data(eventData));
        } catch (IOException e) {
            log.error("Failed to send group chat start event", e);
        }
    }

    private void sendGroupChatCompleteEvent(Long userId, List<Long> messageIds) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) return;
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("type", "group_complete");
            eventData.put("messageIds", messageIds);
            emitter.send(SseEmitter.event()
                .name("group_complete")
                .data(eventData));
            pendingAgentMessages.remove(userId);
        } catch (IOException e) {
            log.error("Failed to send group chat complete event", e);
        }
    }

    private void sendGroupChatErrorEvent(Long userId, String error) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) return;
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("type", "group_error");
            eventData.put("error", error);
            emitter.send(SseEmitter.event()
                .name("group_error")
                .data(eventData));
            pendingAgentMessages.remove(userId);
        } catch (IOException e) {
            log.error("Failed to send group chat error event", e);
        }
    }

    private void updateAgentMessage(Long userId, Long messageId, String content, Long agentId) {
        // Update in database
        Message update = new Message();
        update.setId(messageId);
        update.setContent(content);
        messageMapper.updateById(update);
    }

    private void sendStreamingUpdate(Long userId, Long messageId, String content, Agent agent) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) return;

        try {
            MessageVO vo = new MessageVO();
            vo.setId(messageId);
            vo.setSenderType(SenderType.AGENT.name());
            vo.setSenderId(agent != null ? agent.getId() : 0L);
            vo.setSenderName(agent != null ? agent.getName() : "Agent");
            vo.setSenderAvatar(agent != null ? agent.getAvatarUrl() : null);
            vo.setContent(content);
            vo.setMessageType(MessageType.TEXT.name());
            vo.setBlocks(new ArrayList<>());

            emitter.send(SseEmitter.event()
                .name("streaming")
                .data(vo));
        } catch (IOException e) {
            log.error("Failed to send streaming update", e);
            emitter.complete();
            activeEmitters.remove(userId);
        }
    }

    private void completeAgentMessage(Long userId, Long messageId, String content, String error) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) return;

        try {
            // Update final content in database
            Message update = new Message();
            update.setId(messageId);
            update.setContent(error != null ? error : content);
            messageMapper.updateById(update);

            // Send final completion event
            MessageVO vo = new MessageVO();
            vo.setId(messageId);
            vo.setSenderType(SenderType.AGENT.name());
            vo.setContent(error != null ? error : content);
            vo.setMessageType(MessageType.TEXT.name());
            vo.setBlocks(new ArrayList<>());

            emitter.send(SseEmitter.event()
                .name("complete")
                .data(vo));

            pendingAgentMessages.remove(userId);
        } catch (IOException e) {
            log.error("Failed to send completion", e);
            emitter.complete();
            activeEmitters.remove(userId);
        }
    }

    public List<MessageVO> getConversationMessages(Long conversationId, User currentUser) {
        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, conversationId)
            .eq(ConversationParticipant::getUserId, currentUser.getId());
        if (participantMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // Try cache first
        if (sessionCacheService != null) {
            List<MessageVO> cached = sessionCacheService.getCachedMessages(conversationId);
            if (!cached.isEmpty()) {
                return cached;
            }
        }

        // Order by ID (which corresponds to creation order)
        LambdaQueryWrapper<Message> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(Message::getConversationId, conversationId)
            .orderByAsc(Message::getId);
        List<Message> messages = messageMapper.selectList(msgWrapper);

        List<MessageVO> result = messages.stream()
            .map(this::buildMessageVO)
            .collect(Collectors.toList());

        // Cache the result
        if (sessionCacheService != null && !result.isEmpty()) {
            sessionCacheService.cacheMessages(conversationId, result);
        }

        return result;
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        activeEmitters.put(userId, emitter);

        emitter.onCompletion(() -> activeEmitters.remove(userId));
        emitter.onTimeout(() -> activeEmitters.remove(userId));
        emitter.onError(e -> activeEmitters.remove(userId));

        try {
            emitter.send(SseEmitter.event().name("connected").data("Connected"));
        } catch (IOException e) {
            emitter.complete();
            activeEmitters.remove(userId);
        }

        return emitter;
    }

    private List<Message> getConversationHistory(Long conversationId, int limit) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, conversationId)
            .orderByAsc(Message::getId);
        List<Message> messages = messageMapper.selectList(wrapper);
        // Skip the last message (current one being processed) and take up to 'limit' messages
        if (messages.size() > 1) {
            messages = messages.subList(0, Math.min(messages.size() - 1, limit));
        } else {
            messages = new ArrayList<>();
        }
        log.debug("Retrieved {} history messages for conversation {}", messages.size(), conversationId);
        return messages;
    }

    private void updateConversationTimestampAndTitle(Long conversationId, String firstMessage) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) return;

        boolean needsUpdate = false;

        // Update timestamp
        conversation.setUpdatedAt(null); // Let MyBatis handle it
        needsUpdate = true;

        // Auto-generate title if it's a default title (first message scenario)
        if (firstMessage != null && !firstMessage.isEmpty()) {
            String currentTitle = conversation.getTitle();
            boolean isDefaultTitle = currentTitle == null ||
                currentTitle.startsWith("Chat with") ||
                currentTitle.startsWith("Group with") ||
                currentTitle.equals("New Chat");

            if (isDefaultTitle) {
                String newTitle = firstMessage.length() > 50
                    ? firstMessage.substring(0, 50) + "..."
                    : firstMessage;
                conversation.setTitle(newTitle);
            }
        }

        if (needsUpdate) {
            conversationMapper.updateById(conversation);
        }
    }

    private MessageVO buildMessageVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setSenderType(message.getSenderType());
        vo.setSenderId(message.getSenderId());
        vo.setContent(message.getContent());
        vo.setMessageType(message.getMessageType());
        vo.setParentId(message.getParentId());
        vo.setPinned(message.getPinned() != null ? message.getPinned() : false);
        vo.setStatus(message.getStatus());
        vo.setCreatedAt(message.getCreatedAt());

        if (SenderType.USER.name().equals(message.getSenderType())) {
            vo.setSenderName("You");
            vo.setSenderAvatar(null);
        } else {
            Agent sender = agentMapper.selectById(message.getSenderId());
            if (sender != null) {
                vo.setSenderName(sender.getName());
                vo.setSenderAvatar(sender.getAvatarUrl());
            }
        }

        LambdaQueryWrapper<MessageBlock> blockWrapper = new LambdaQueryWrapper<>();
        blockWrapper.eq(MessageBlock::getMessageId, message.getId())
            .orderByAsc(MessageBlock::getSortOrder);
        List<MessageBlock> blocks = messageBlockMapper.selectList(blockWrapper);

        List<MessageVO.MessageBlockVO> blockVOs = blocks.stream()
            .map(b -> {
                MessageVO.MessageBlockVO bv = new MessageVO.MessageBlockVO();
                bv.setId(b.getId());
                bv.setBlockType(b.getBlockType());
                bv.setContent(b.getContent());
                bv.setLanguage(b.getLanguage());
                bv.setMetadata(b.getMetadata());
                return bv;
            })
            .collect(Collectors.toList());
        vo.setBlocks(blockVOs);

        return vo;
    }

    @Transactional
    public void pinMessage(Long messageId, Long userId, boolean pinned) {
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Message not found");
        }

        // Verify user has access to this conversation
        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, message.getConversationId())
            .eq(ConversationParticipant::getUserId, userId);
        if (participantMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Access denied");
        }

        message.setPinned(pinned);
        messageMapper.updateById(message);
    }

    public List<MessageVO> getPinnedMessages(Long conversationId, Long userId) {
        // Verify user has access
        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, conversationId)
            .eq(ConversationParticipant::getUserId, userId);
        if (participantMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Access denied");
        }

        LambdaQueryWrapper<Message> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(Message::getConversationId, conversationId)
            .eq(Message::getPinned, true)
            .orderByAsc(Message::getCreatedAt);
        List<Message> messages = messageMapper.selectList(msgWrapper);

        return messages.stream()
            .map(this::buildMessageVO)
            .collect(Collectors.toList());
    }

    public List<MessageBlock> getMessageBlocks(Long messageId, Long userId) {
        // Verify user has access to this message's conversation
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Message not found");
        }

        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, message.getConversationId())
            .eq(ConversationParticipant::getUserId, userId);
        if (participantMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Access denied");
        }

        LambdaQueryWrapper<MessageBlock> blockWrapper = new LambdaQueryWrapper<>();
        blockWrapper.eq(MessageBlock::getMessageId, messageId)
            .orderByAsc(MessageBlock::getSortOrder);
        return messageBlockMapper.selectList(blockWrapper);
    }

    @Transactional
    public MessageVersion saveVersion(Long messageId, Long userId, String content) {
        // Verify access
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Message not found");
        }

        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, message.getConversationId())
            .eq(ConversationParticipant::getUserId, userId);
        if (participantMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // Get next version number
        LambdaQueryWrapper<MessageVersion> versionWrapper = new LambdaQueryWrapper<>();
        versionWrapper.eq(MessageVersion::getMessageId, messageId)
            .orderByDesc(MessageVersion::getVersionNumber);
        List<MessageVersion> existingVersions = messageVersionMapper.selectList(versionWrapper);
        int nextVersion = existingVersions.isEmpty() ? 1 : existingVersions.get(0).getVersionNumber() + 1;

        // Save new version
        MessageVersion version = new MessageVersion();
        version.setMessageId(messageId);
        version.setContent(content);
        version.setVersionNumber(nextVersion);
        messageVersionMapper.insert(version);

        // Update message with new content
        message.setContent(content);
        messageMapper.updateById(message);

        return version;
    }

    public List<MessageVersion> getMessageVersions(Long messageId, Long userId) {
        // Verify access
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Message not found");
        }

        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, message.getConversationId())
            .eq(ConversationParticipant::getUserId, userId);
        if (participantMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Access denied");
        }

        LambdaQueryWrapper<MessageVersion> versionWrapper = new LambdaQueryWrapper<>();
        versionWrapper.eq(MessageVersion::getMessageId, messageId)
            .orderByDesc(MessageVersion::getVersionNumber);
        return messageVersionMapper.selectList(versionWrapper);
    }
}
