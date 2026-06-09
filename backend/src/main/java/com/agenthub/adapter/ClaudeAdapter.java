package com.agenthub.adapter;

import com.agenthub.agent.AgentAdapter;
import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.agent.model.ProviderMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ClaudeAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAdapter.class);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)?\\n?([\\s\\S]*?)```");

    @Value("${anthropic.api.key:}")
    private String apiKey;

    @Value("${anthropic.api.base:https://api.anthropic.com}")
    private String apiBase;

    @Value("${anthropic.model:claude-3-haiku-20240307}")
    private String modelName;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ClaudeAdapter() {
        this.webClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com")
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AgentResponse generate(AgentRequest request) {
        if (!isAvailable()) {
            return createFallbackResponse("Claude API key not configured");
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("max_tokens", 1024);

            List<Map<String, String>> messages = new ArrayList<>();

            if (request.getHistory() != null) {
                for (var msg : request.getHistory()) {
                    Map<String, String> histMsg = new HashMap<>();
                    histMsg.put("role", "USER".equals(msg.getSenderType()) ? "user" : "assistant");
                    histMsg.put("content", msg.getContent());
                    messages.add(histMsg);
                }
            }

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", request.getContent());
            messages.add(userMsg);

            requestBody.put("messages", messages);

            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                requestBody.put("system", request.getSystemPrompt());
            }

            log.debug("Calling Claude API with model: {}", modelName);

            String response = webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .retry(1)
                .block();

            return parseResponse(response);

        } catch (Exception e) {
            log.error("Claude API call failed", e);
            return createFallbackResponse("Claude API error: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> generateStream(AgentRequest request) {
        return Flux.just("Streaming not implemented for Claude adapter");
    }

    @Override
    public ProviderMeta getProviderMeta() {
        ProviderMeta meta = new ProviderMeta();
        meta.setProviderCode("ANTHROPIC");
        meta.setProviderName("Anthropic Claude");
        meta.setModelName(modelName);
        meta.setAvailable(isAvailable());
        return meta;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    private AgentResponse parseResponse(String response) {
        AgentResponse agentResponse = new AgentResponse();

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.get("content");

            if (content != null && content.isArray() && content.size() > 0) {
                String text = content.get(0).get("text").asText();
                agentResponse.setContent(text);
                agentResponse.setFinishReason("stop");

                extractCodeBlocks(text, agentResponse);
            } else {
                agentResponse.setContent("Unexpected response format: " + response);
                agentResponse.setFinishReason("error");
            }
        } catch (Exception e) {
            log.error("Failed to parse Claude response", e);
            agentResponse.setContent("Failed to parse response: " + e.getMessage());
            agentResponse.setFinishReason("error");
        }

        return agentResponse;
    }

    private void extractCodeBlocks(String content, AgentResponse response) {
        if (content == null) return;

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);

        while (matcher.find()) {
            String language = matcher.group(1);
            String code = matcher.group(2);

            if (language != null && !language.isEmpty()) {
                response.addCodeBlock(code.trim(), language);
            } else {
                response.addCodeBlock(code.trim(), "text");
            }
        }
    }

    private AgentResponse createFallbackResponse(String reason) {
        AgentResponse response = new AgentResponse();
        response.setContent(reason + ". Please configure ANTHROPIC_API_KEY or use built-in agent.");
        response.setFinishReason("fallback");
        return response;
    }
}
