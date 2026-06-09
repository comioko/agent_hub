package com.agenthub.adapter;

import com.agenthub.agent.AgentAdapter;
import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.agent.model.ProviderMeta;
import com.agenthub.exception.AgentException;
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
public class OpenAIAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAIAdapter.class);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)?\\n?([\\s\\S]*?)```");

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.base:https://api.openai.com/v1}")
    private String apiBase;

    @Value("${openai.model:gpt-3.5-turbo}")
    private String modelName;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAIAdapter() {
        this.webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AgentResponse generate(AgentRequest request) {
        if (!isAvailable()) {
            return createFallbackResponse("OpenAI API key not configured");
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);

            List<Map<String, String>> messages = new ArrayList<>();

            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                Map<String, String> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", request.getSystemPrompt());
                messages.add(systemMsg);
            }

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

            log.debug("Calling OpenAI API with model: {}", modelName);

            String response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .retry(1)
                .block();

            return parseResponse(response);

        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            return createFallbackResponse("OpenAI API error: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> generateStream(AgentRequest request) {
        if (!isAvailable()) {
            return Flux.just("OpenAI API key not configured");
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("stream", true);

            List<Map<String, String>> messages = new ArrayList<>();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                Map<String, String> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", request.getSystemPrompt());
                messages.add(systemMsg);
            }

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", request.getContent());
            messages.add(userMsg);

            requestBody.put("messages", messages);

            return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(120))
                .map(this::extractStreamContent);

        } catch (Exception e) {
            return Flux.just("Error: " + e.getMessage());
        }
    }

    @Override
    public ProviderMeta getProviderMeta() {
        ProviderMeta meta = new ProviderMeta();
        meta.setProviderCode("OPENAI");
        meta.setProviderName("OpenAI");
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
            JsonNode choices = root.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                String content = message != null ? message.get("content").asText() : "";

                agentResponse.setContent(content);
                agentResponse.setFinishReason(choices.get(0).get("finish_reason").asText());

                extractCodeBlocks(content, agentResponse);
            } else {
                agentResponse.setContent("Unexpected response format: " + response);
                agentResponse.setFinishReason("error");
            }
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response", e);
            agentResponse.setContent("Failed to parse response: " + e.getMessage());
            agentResponse.setFinishReason("error");
        }

        return agentResponse;
    }

    private void extractCodeBlocks(String content, AgentResponse response) {
        if (content == null) return;

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        String remaining = content;

        while (matcher.find()) {
            String language = matcher.group(1);
            String code = matcher.group(2);

            if (language != null && !language.isEmpty()) {
                response.addCodeBlock(code.trim(), language);
            } else {
                response.addCodeBlock(code.trim(), "text");
            }

            remaining = remaining.replace(matcher.group(0), "");
        }
    }

    private String extractStreamContent(String chunk) {
        try {
            JsonNode node = objectMapper.readTree(chunk);
            JsonNode choices = node.get("choices");
            if (choices != null && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    return delta.get("content").asText();
                }
            }
        } catch (Exception e) {
            log.trace("Failed to parse stream chunk: {}", chunk);
        }
        return "";
    }

    private AgentResponse createFallbackResponse(String reason) {
        AgentResponse response = new AgentResponse();
        response.setContent(reason + ". Please configure OPENAI_API_KEY or use built-in agent.");
        response.setFinishReason("fallback");
        return response;
    }
}
