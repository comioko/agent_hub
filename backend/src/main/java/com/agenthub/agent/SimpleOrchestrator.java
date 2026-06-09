package com.agenthub.agent;

import com.agenthub.agent.model.AgentResponse;
import com.agenthub.agent.model.AgentTask;
import com.agenthub.model.entity.Agent;
import com.agenthub.model.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SimpleOrchestrator implements Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(SimpleOrchestrator.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    @Override
    public boolean shouldOrchestrate(Message message) {
        if (message == null || message.getContent() == null) {
            return false;
        }
        boolean hasMention = message.getContent().contains("@");
        log.debug("shouldOrchestrate for message {}: {}", message.getId(), hasMention);
        return hasMention;
    }

    @Override
    public List<AgentTask> decompose(String userMessage, List<Agent> participants) {
        if (userMessage == null || participants == null || participants.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> mentions = extractMentions(userMessage);
        if (mentions.isEmpty()) {
            return new ArrayList<>();
        }

        log.info("Decomposing message with mentions: {}", mentions);

        List<AgentTask> tasks = new ArrayList<>();
        for (String mention : mentions) {
            String cleanMention = mention.toLowerCase();

            Agent matchedAgent = participants.stream()
                .filter(a -> a.getName() != null &&
                           a.getName().toLowerCase().equals(cleanMention))
                .findFirst()
                .orElse(null);

            if (matchedAgent == null) {
                matchedAgent = participants.stream()
                    .filter(a -> a.getName() != null &&
                               a.getName().toLowerCase().contains(cleanMention))
                    .findFirst()
                    .orElse(null);
            }

            if (matchedAgent != null) {
                AgentTask task = new AgentTask();
                task.setAgentId(matchedAgent.getId());
                task.setTaskDescription(cleanMessage(userMessage, mention));
                tasks.add(task);
                log.debug("Created task for agent: {} ({})", matchedAgent.getName(), matchedAgent.getId());
            } else {
                log.warn("No agent found for mention: {}", mention);
            }
        }

        return tasks;
    }

    @Override
    public String aggregate(List<AgentResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "No response from agents.";
        }

        if (responses.size() == 1) {
            return responses.get(0).getContent();
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < responses.size(); i++) {
            AgentResponse r = responses.get(i);
            sb.append("## Response ").append(i + 1).append("\n\n");
            sb.append(r.getContent()).append("\n\n");
        }

        return sb.toString().trim();
    }

    private List<String> extractMentions(String message) {
        List<String> mentions = new ArrayList<>();
        if (message == null) {
            return mentions;
        }

        Matcher matcher = MENTION_PATTERN.matcher(message);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }

        return mentions;
    }

    private String cleanMessage(String message, String mention) {
        if (message == null) {
            return "";
        }
        return message.replaceAll("@" + mention, "").trim();
    }
}
