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
import com.agenthub.service.ToolExecutor;
import com.agenthub.agent.model.ToolExecutionResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.agenthub.util.TriConsumer;

@Component
public class GroupAgentHandler {

    private static final Logger log = LoggerFactory.getLogger(GroupAgentHandler.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^\\]]+)\\]|@([^\\s@]+)");
    private static final int HISTORY_LIMIT = 20;

    private final Orchestrator orchestrator;
    private final AgentCore agentCore;
    private final AgentMapper agentMapper;
    private final ConversationParticipantMapper participantMapper;
    private final MessageMapper messageMapper;
    private final ToolExecutor toolExecutor;

    public GroupAgentHandler(Orchestrator orchestrator,
                            AgentCore agentCore,
                            AgentMapper agentMapper,
                            ConversationParticipantMapper participantMapper,
                            MessageMapper messageMapper,
                            ToolExecutor toolExecutor) {
        this.orchestrator = orchestrator;
        this.agentCore = agentCore;
        this.agentMapper = agentMapper;
        this.participantMapper = participantMapper;
        this.messageMapper = messageMapper;
        this.toolExecutor = toolExecutor;
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
        Set<String> processedAgents = new HashSet<>();
        Map<String, String> pendingHandoffs = new LinkedHashMap<>();

        // Check for supervisor agent
        Agent supervisor = findOrchestratorAgent(participants);

        // Process user message mentions first
        Map<String, String> userMentions = detectMentionsInText(userMessage.getContent(), participants);
        pendingHandoffs.putAll(userMentions);

        // Check if user @mentioned the supervisor directly (intending to delegate)
        boolean userMentionedSupervisor = supervisor != null && pendingHandoffs.containsKey(supervisor.getName().toLowerCase());

        if (supervisor != null && (pendingHandoffs.isEmpty() || userMentionedSupervisor)) {
            // Supervisor mode: call supervisor first, it will delegate
            // This applies when:
            // 1. No @mentions in user message (supervisor decides who to involve)
            // 2. User @mentioned the supervisor directly (user wants supervisor to orchestrate)
            log.info("Supervisor mode detected, calling supervisor: {}", supervisor.getName());

            // Remove supervisor mention from handoffs if present (we'll handle it specially)
            if (userMentionedSupervisor) {
                pendingHandoffs.remove(supervisor.getName().toLowerCase());
            }

            // Build instructions for supervisor with available agents
            String availableAgents = buildAvailableAgentsInfo(participants);
            String supervisorInstructions = String.format(
                "你是团队的主管 (Orchestrator)。\n\n可用子代理:\n%s\n\n规则:\n1. 只能使用上述列表中的代理名称进行 @ 委托\n2. 每个 @代理名 必须精确匹配列表中的名称\n3. 分解任务后使用 @代理名 委托给子代理\n4. 等待子代理完成后汇总结果\n5. 你必须实际委托任务给子代理，不要只输出计划\n6. 用户只跟你对话，不要直接回答用户的问题，而是分解任务后委托给子代理\n\n用户需求:\n%s",
                availableAgents, userMessage.getContent());

            // Pass full instructions as content (not in delegation context to avoid duplication)
            String response = callAgentSync(userId, supervisor, supervisorInstructions, userMessage, messageIds, sendStreamingUpdate, null);
            processedAgents.add(supervisor.getName().toLowerCase());

            // Check supervisor's response for @mentions
            Map<String, String> supervisorMentions = detectMentionsInText(response, participants);

            log.info("Supervisor response: {}", response.substring(0, Math.min(200, response.length())));
            log.info("Detected supervisor mentions: {}", supervisorMentions.keySet());

            pendingHandoffs.putAll(supervisorMentions);

            // Process any handoffs from supervisor
            if (!pendingHandoffs.isEmpty()) {
                // Remove supervisor from processed agents so sub-agents can be delegated
                processedAgents.remove(supervisor.getName().toLowerCase());
                processHandoffs(pendingHandoffs, processedAgents, userMessage, userId, messageIds, sendStreamingUpdate);
            }
        } else if (pendingHandoffs.isEmpty()) {
            // No @mentions in user message
            // When there's NO supervisor, call all agents (legacy behavior)
            // When there IS a supervisor but user @mentioned specific agent(s), process those mentions
            if (supervisor == null) {
                log.debug("No @mentions in user message, calling all agents");
                for (Agent agent : participants) {
                    String response = callAgentSync(userId, agent, userMessage.getContent(), userMessage, messageIds, sendStreamingUpdate);
                    // Check if agent's response contains @mentions for further handoff
                    Map<String, String> agentMentions = detectMentionsInText(response, participants);
                    for (Map.Entry<String, String> entry : agentMentions.entrySet()) {
                        if (!processedAgents.contains(entry.getKey().toLowerCase())) {
                            pendingHandoffs.put(entry.getKey(), entry.getValue());
                        }
                    }
                    processedAgents.add(agent.getName().toLowerCase());
                }

                // Process any handoffs from agents
                if (!pendingHandoffs.isEmpty()) {
                    processHandoffs(pendingHandoffs, processedAgents, userMessage, userId, messageIds, sendStreamingUpdate);
                }
            }
            // When there IS a supervisor but no @mentions, supervisor should have been called above
            // If we reach here, it means supervisor mode was not triggered - do nothing (or call supervisor as fallback)
            log.debug("No @mentions and no supervisor - no action taken in group chat");
        } else {
            // Process @mentions
            processHandoffs(pendingHandoffs, processedAgents, userMessage, userId, messageIds, sendStreamingUpdate);
        }

        // Send completion event
        sendComplete.accept(userId, messageIds);
    }

    private Agent findOrchestratorAgent(List<Agent> participants) {
        return participants.stream()
            .filter(a -> a.getIsOrchestrator() != null && a.getIsOrchestrator())
            .findFirst()
            .orElse(null);
    }

    private void processHandoffs(Map<String, String> handoffs, Set<String> processedAgents,
            Message parentMessage, Long userId, List<Long> messageIds,
            TriConsumer<Long, Long, String, Agent> sendStreamingUpdate) {

        List<Agent> participants = getConversationAgents(parentMessage.getConversationId());
        processHandoffs(handoffs, processedAgents, parentMessage, userId, messageIds, sendStreamingUpdate, participants);
    }

    private void processHandoffs(Map<String, String> handoffs, Set<String> processedAgents,
            Message parentMessage, Long userId, List<Long> messageIds,
            TriConsumer<Long, Long, String, Agent> sendStreamingUpdate,
            List<Agent> validParticipants) {

        // Deep copy to avoid modification during iteration
        Map<String, String> remainingHandoffs = new LinkedHashMap<>(handoffs);

        // Collect responses from agents for potential chaining
        Map<String, String> agentResponses = new HashMap<>();

        // Phase 1: Execute all initial handoffs in PARALLEL (they are independent tasks)
        List<Map.Entry<String, String>> initialWave = new ArrayList<>(remainingHandoffs.entrySet());
        remainingHandoffs.clear();

        log.info("Phase 1: Executing {} agents in parallel", initialWave.size());

        // Execute initial wave in parallel
        if (!initialWave.isEmpty()) {
            Map<String, String> parallelResponses = executeAgentsInParallel(
                initialWave, parentMessage, userId, messageIds, sendStreamingUpdate, processedAgents, validParticipants);

            // Collect responses and check for new handoffs
            for (Map.Entry<String, String> entry : initialWave) {
                String agentName = entry.getKey();
                String cleanName = normalizeAgentName(agentName);
                agentResponses.put(cleanName, parallelResponses.getOrDefault(agentName, ""));

                // Check if this agent's response contains @mentions for next wave
                String response = parallelResponses.getOrDefault(agentName, "");
                Map<String, String> agentMentions = detectMentionsInText(response, validParticipants);
                for (Map.Entry<String, String> mention : agentMentions.entrySet()) {
                    String mentionedAgent = mention.getKey();
                    String mentionedCleanName = normalizeAgentName(mentionedAgent);
                    if (!processedAgents.contains(mentionedCleanName.toLowerCase())) {
                        remainingHandoffs.put(mentionedAgent, mention.getValue());
                        log.info("Agent {} triggered handoff to @{}", cleanName, mentionedAgent);
                    }
                }
            }
        }

        // Phase 2+: Process remaining handoffs sequentially (these may depend on previous results)
        while (!remainingHandoffs.isEmpty()) {
            Iterator<Map.Entry<String, String>> iterator = remainingHandoffs.entrySet().iterator();
            Map.Entry<String, String> entry = iterator.next();
            iterator.remove();

            String agentName = entry.getKey();
            String taskContent = entry.getValue();
            String cleanName = normalizeAgentName(agentName);

            if (processedAgents.contains(cleanName.toLowerCase())) {
                log.debug("Agent {} already processed, skipping", cleanName);
                continue;
            }

            Agent targetAgent = findAgentByName(cleanName, parentMessage.getConversationId());
            if (targetAgent == null) {
                log.warn("Agent not found for @mention: {}", cleanName);
                continue;
            }

            processedAgents.add(cleanName.toLowerCase());

            log.info("Delegating task to agent: {} (sequential)", targetAgent.getName());

            // Build delegation context with previous agent responses for context
            String delegationContext = buildDelegationContext(targetAgent.getName(), taskContent, agentResponses);

            String response = callAgentSync(userId, targetAgent, taskContent, parentMessage, messageIds, sendStreamingUpdate, delegationContext);
            agentResponses.put(targetAgent.getName(), response);

            // Check for new handoffs
            Map<String, String> agentMentions = detectMentionsInText(response, validParticipants);
            for (Map.Entry<String, String> mention : agentMentions.entrySet()) {
                String mentionedAgent = mention.getKey();
                String mentionedCleanName = normalizeAgentName(mentionedAgent);
                if (!processedAgents.contains(mentionedCleanName.toLowerCase())) {
                    remainingHandoffs.put(mentionedAgent, mention.getValue());
                    log.info("Agent {} triggered handoff to @{}", targetAgent.getName(), mentionedAgent);
                }
            }
        }
    }

    private String normalizeAgentName(String agentName) {
        if (agentName.startsWith("[")) {
            return agentName.replaceAll("^\\[|\\]$", "");
        }
        return agentName;
    }

    private Map<String, String> executeAgentsInParallel(List<Map.Entry<String, String>> tasks,
            Message parentMessage, Long userId, List<Long> messageIds,
            TriConsumer<Long, Long, String, Agent> sendStreamingUpdate,
            Set<String> processedAgents, List<Agent> validParticipants) {

        Map<String, String> responses = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(tasks.size());

        for (Map.Entry<String, String> entry : tasks) {
            String agentName = entry.getKey();
            String taskContent = entry.getValue();
            String cleanName = normalizeAgentName(agentName);

            if (processedAgents.contains(cleanName.toLowerCase())) {
                log.debug("Agent {} already processed, skipping", cleanName);
                latch.countDown();
                continue;
            }

            Agent targetAgent = findAgentByName(cleanName, parentMessage.getConversationId());
            if (targetAgent == null) {
                log.warn("Agent not found for @mention: {}", cleanName);
                latch.countDown();
                continue;
            }

            processedAgents.add(cleanName.toLowerCase());

            // Run each agent in its own thread
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Starting parallel execution for agent: {}", targetAgent.getName());

                    String delegationContext = buildDelegationContext(targetAgent.getName(), taskContent, new HashMap<>());
                    String response = callAgentSync(userId, targetAgent, taskContent, parentMessage, messageIds, sendStreamingUpdate, delegationContext);

                    responses.put(agentName, response);
                } catch (Exception e) {
                    log.error("Error in parallel execution for agent {}: {}", targetAgent.getName(), e.getMessage());
                    responses.put(agentName, "Error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(5, TimeUnit.MINUTES); // Wait up to 5 minutes for all agents
        } catch (InterruptedException e) {
            log.error("Parallel execution interrupted");
            Thread.currentThread().interrupt();
        }

        return responses;
    }

    private String buildDelegationContext(String agentName, String taskContent, Map<String, String> previousResponses) {
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append(String.format(
            "【重要】你是 %s。你正在接受主管(Supervisor)的任务委托。\n\n", agentName));
        contextBuilder.append("任务内容:\n").append(taskContent).append("\n\n");

        if (!previousResponses.isEmpty()) {
            contextBuilder.append("其他代理已完成的工作（供参考）:\n");
            for (Map.Entry<String, String> resp : previousResponses.entrySet()) {
                // Truncate long responses
                String result = resp.getValue();
                if (result.length() > 2000) {
                    result = result.substring(0, 2000) + "...(已截断)";
                }
                contextBuilder.append(String.format("- %s 的结果:\n%s\n\n", resp.getKey(), result));
            }
        }

        contextBuilder.append("请专注于完成你的任务：\n");
        contextBuilder.append("1. 只输出任务结果，不要输出过多解释\n");
        contextBuilder.append("2. 不要询问任何问题\n");
        contextBuilder.append("3. 完成后直接返回你的工作成果\n");
        contextBuilder.append("4. 如果需要执行shell命令，请使用 ```bash 代码块 ```，系统会自动执行并返回结果\n");

        return contextBuilder.toString();
    }

    private Map<String, String> detectMentionsInText(String text, List<Agent> validParticipants) {
        Map<String, String> mentions = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return mentions;
        }

        // Build a set of valid agent names (lowercase) for quick lookup
        Set<String> validNamesLower = new HashSet<>();
        Map<String, String> lowerToOriginal = new HashMap<>(); // lowercase -> original name
        for (Agent a : validParticipants) {
            if (a.getName() != null) {
                String lower = a.getName().toLowerCase();
                validNamesLower.add(lower);
                lowerToOriginal.put(lower, a.getName());
            }
        }

        // Find all valid mentions with their positions
        List< MentionMatch> matches = new ArrayList<>();
        Matcher matcher = MENTION_PATTERN.matcher(text);
        while (matcher.find()) {
            // group(1) is bracket syntax name, group(2) is regular word mention
            String agentName = matcher.group(1);
            if (agentName == null) {
                agentName = matcher.group(2);
            }
            if (agentName == null) continue;

            // Normalize bracket syntax: "[Claude Code]" -> "Claude Code"
            if (agentName.startsWith("[")) {
                agentName = agentName.replaceAll("^\\[|\\]$", "");
            }

            String lowerName = agentName.toLowerCase();
            if (validNamesLower.contains(lowerName)) {
                String originalName = lowerToOriginal.get(lowerName);
                matches.add(new MentionMatch(matcher.start(), matcher.end(), originalName, agentName));
            }
        }

        // Sort matches by position
        matches.sort((a, b) -> Integer.compare(a.start, b.start));

        // Extract content between mentions
        for (int i = 0; i < matches.size(); i++) {
            MentionMatch match = matches.get(i);
            int contentStart = match.end;
            int contentEnd = (i + 1 < matches.size()) ? matches.get(i + 1).start : text.length();
            String taskContent = text.substring(contentStart, contentEnd).trim();
            mentions.put(match.originalName, taskContent);
        }

        return mentions;
    }

    private static class MentionMatch {
        int start;
        int end;
        String originalName; // Name as it appears in the agent list
        String rawName; // Name as it appears in the text

        MentionMatch(int start, int end, String originalName, String rawName) {
            this.start = start;
            this.end = end;
            this.originalName = originalName;
            this.rawName = rawName;
        }
    }

    private Agent findAgentByName(String agentName, Long conversationId) {
        List<Agent> participants = getConversationAgents(conversationId);

        // Handle bracket syntax: "[Claude Code]" -> "Claude Code"
        String cleanName = agentName;
        if (agentName.startsWith("[")) {
            cleanName = agentName.replaceAll("^\\[|\\]$", "");
        }
        String lowerName = cleanName.toLowerCase();

        log.debug("Finding agent by name: '{}' (clean: '{}', lower: '{}'), participants: {}",
            agentName, cleanName, lowerName, participants.size());

        // Try exact match first
        for (Agent a : participants) {
            log.debug("  Checking agent: '{}' (lower: '{}')", a.getName(), a.getName() != null ? a.getName().toLowerCase() : "null");
        }

        return participants.stream()
            .filter(a -> a.getName() != null && a.getName().toLowerCase().equals(lowerName))
            .findFirst()
            .orElseGet(() -> participants.stream()
                .filter(a -> a.getName() != null && a.getName().toLowerCase().contains(lowerName))
                .findFirst()
                .orElse(null));
    }

    private String callAgentSync(Long userId, Agent agent, String content, Message parentMessage,
            List<Long> messageIds, TriConsumer<Long, Long, String, Agent> sendStreamingUpdate) {

        return callAgentSync(userId, agent, content, parentMessage, messageIds, sendStreamingUpdate, null);
    }

    private String callAgentSync(Long userId, Agent agent, String content, Message parentMessage,
            List<Long> messageIds, TriConsumer<Long, Long, String, Agent> sendStreamingUpdate,
            String delegationContext) {

        log.info("callAgentSync: agent={}, content length={}, hasDelegationContext={}",
            agent.getName(), content != null ? content.length() : 0, delegationContext != null);
        if (delegationContext != null) {
            log.debug("Delegation context:\n{}", delegationContext);
        }

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

        // Build request - do NOT pass full conversation history to sub-agents for isolation
        AgentRequest request = new AgentRequest();
        request.setUserId(userId);
        request.setConversationId(parentMessage.getConversationId());
        request.setContent(content);

        // Build system prompt - if agent has its own prompt, use it; otherwise use delegation context
        String systemPrompt = buildAgentSystemPrompt(agent, delegationContext);
        request.setSystemPrompt(systemPrompt);

        // Sub-agents do NOT get full conversation history to prevent role confusion
        // They only get the task content passed directly to them
        List<Message> history = new ArrayList<>();
        request.setHistory(history);

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

        // Execute any shell commands found in the response
        String response = fullContent.toString();
        return executeShellCommands(response);
    }

    private String buildAvailableAgentsInfo(List<Agent> agents) {
        if (agents == null || agents.isEmpty()) {
            return "无可用代理";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < agents.size(); i++) {
            Agent a = agents.get(i);
            // Use bracket syntax for names with spaces
            String mentionName = a.getName().contains(" ") ? "[" + a.getName() + "]" : a.getName();
            sb.append(String.format("%d. @%s - %s",
                i + 1,
                mentionName,
                a.getDescription() != null ? a.getDescription() : "无描述"));
            if (i < agents.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private List<Message> loadConversationHistory(Long conversationId, int limit) {
        // Smart context selection: prioritize pinned messages, then recent AUTO messages
        // Skip EXCLUDED messages

        // 1. Get all PINNED messages first (ordered by priority descending)
        LambdaQueryWrapper<Message> pinnedWrapper = new LambdaQueryWrapper<>();
        pinnedWrapper.eq(Message::getConversationId, conversationId)
            .eq(Message::getContextType, "PINNED")
            .orderByDesc(Message::getContextPriority)
            .orderByAsc(Message::getId);
        List<Message> pinnedMessages = messageMapper.selectList(pinnedWrapper);

        // 2. Get AUTO messages (most recent first), excluding the current message
        LambdaQueryWrapper<Message> autoWrapper = new LambdaQueryWrapper<>();
        autoWrapper.eq(Message::getConversationId, conversationId)
            .eq(Message::getContextType, "AUTO")
            .orderByDesc(Message::getId);
        List<Message> autoMessages = messageMapper.selectList(autoWrapper);

        // Skip the last message (current one being processed)
        if (autoMessages.size() > 1) {
            autoMessages = autoMessages.subList(0, autoMessages.size() - 1);
        } else {
            autoMessages = new ArrayList<>();
        }

        // 3. Build final context: pinned first, then fill with recent AUTO up to limit
        List<Message> result = new ArrayList<>();
        result.addAll(pinnedMessages);

        int remainingSlots = limit - result.size();
        if (remainingSlots > 0 && !autoMessages.isEmpty()) {
            // Take the most recent AUTO messages
            int takeCount = Math.min(remainingSlots, autoMessages.size());
            // Reverse to get chronological order (autoMessages is ordered by id desc)
            List<Message> recentAuto = autoMessages.subList(0, takeCount);
            Collections.reverse(recentAuto);
            result.addAll(recentAuto);
        }

        log.debug("Retrieved {} history messages for conversation {} ({} pinned, {} auto)",
            result.size(), conversationId, pinnedMessages.size(), Math.min(limit - pinnedMessages.size(), autoMessages.size()));
        return result;
    }

    /**
     * Build system prompt for an agent.
     * If agent has its own system prompt, prepend delegation context.
     * If agent has no system prompt, use delegation context as default.
     */
    private String buildAgentSystemPrompt(Agent agent, String delegationContext) {
        String agentPrompt = agent.getSystemPrompt();

        StringBuilder fullPrompt = new StringBuilder();

        // Add delegation context if available
        if (delegationContext != null && !delegationContext.isEmpty()) {
            fullPrompt.append(delegationContext).append("\n\n");
        }

        // Add agent's own prompt if exists
        if (agentPrompt != null && !agentPrompt.trim().isEmpty()) {
            fullPrompt.append(agentPrompt);
        } else if (fullPrompt.length() == 0) {
            fullPrompt.append(String.format("你是 %s。请专注于完成用户的任务。", agent.getName()));
        }

        return fullPrompt.toString();
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

    /**
     * Extract and execute shell commands from agent response.
     * Looks for patterns like: ```bash\ncommand\n``` or $ command
     */
    private String executeShellCommands(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }

        StringBuilder result = new StringBuilder(response);

        // Pattern 1: ```bash\ncommand\n```
        Pattern bashBlockPattern = Pattern.compile("```bash\\s*\\n([\\s\\S]*?)\\n```");
        Matcher bashBlockMatcher = bashBlockPattern.matcher(response);

        while (bashBlockMatcher.find()) {
            String command = bashBlockMatcher.group(1).trim();
            if (!command.isEmpty()) {
                log.info("Executing bash command from code block: {}", command);
                ToolExecutionResult execResult = toolExecutor.execute("bash", Map.of("command", command));
                String output = execResult.getResult();
                if (!execResult.isSuccess()) {
                    output = "Command failed: " + output;
                }
                result.append("\n\n[Shell执行结果]:\n").append(output).append("\n");
            }
        }

        // Pattern 2: Lines starting with $ or >
        Pattern shellLinePattern = Pattern.compile("^\\$\\s*(.+)$|^>\\s*(.+)$", Pattern.MULTILINE);
        Matcher shellLineMatcher = shellLinePattern.matcher(response);

        while (shellLineMatcher.find()) {
            String command = shellLineMatcher.group(1) != null ? shellLineMatcher.group(1) : shellLineMatcher.group(2);
            if (command != null && !command.trim().isEmpty()) {
                log.info("Executing shell command: {}", command);
                ToolExecutionResult execResult = toolExecutor.execute("bash", Map.of("command", command.trim()));
                String output = execResult.getResult();
                if (!execResult.isSuccess()) {
                    output = "Command failed: " + output;
                }
                result.append("\n\n[Shell执行结果]:\n").append(output).append("\n");
            }
        }

        // Pattern 3: Inline commands like `cat file.txt` or $(command)
        Pattern inlinePattern = Pattern.compile("`([^`]+)`");
        Matcher inlineMatcher = inlinePattern.matcher(response);

        while (inlineMatcher.find()) {
            String potentialCommand = inlineMatcher.group(1).trim();
            // Only execute if it looks like a shell command (contains common shell patterns)
            if (potentialCommand.contains("cat ") || potentialCommand.contains("ls ") ||
                potentialCommand.contains("pwd") || potentialCommand.contains("echo ") ||
                potentialCommand.contains("mkdir ") || potentialCommand.contains("touch ") ||
                potentialCommand.startsWith("cd ") || potentialCommand.startsWith("cp ") ||
                potentialCommand.startsWith("mv ") || potentialCommand.startsWith("rm ") ||
                potentialCommand.contains("grep ") || potentialCommand.contains("find ") ||
                potentialCommand.startsWith("npm ") || potentialCommand.startsWith("pip ") ||
                potentialCommand.startsWith("python") || potentialCommand.startsWith("node") ||
                potentialCommand.startsWith("git ")) {

                log.info("Executing inline shell command: {}", potentialCommand);
                ToolExecutionResult execResult = toolExecutor.execute("bash", Map.of("command", potentialCommand));
                String output = execResult.getResult();
                result.append("\n\n[命令 `").append(potentialCommand).append("` 执行结果]:\n").append(output).append("\n");
            }
        }

        return result.toString();
    }
}
