package com.agenthub.adapter;

import com.agenthub.agent.AgentAdapter;
import com.agenthub.agent.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VolcanoAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(VolcanoAdapter.class);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)?\\n?([\\s\\S]*?)```");

    @Value("${volcano.api.key:}")
    private String apiKey;

    @Value("${volcano.api.base:https://ark.cn-beijing.volces.com/api/v3}")
    private String apiBase;

    @Value("${volcano.endpoint:}")
    private String endpointId;

    @Value("${volcano.model:doubao-pro-32k}")
    private String modelName;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public VolcanoAdapter() {
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AgentResponse generate(AgentRequest request) {
        if (!isAvailable()) {
            return createFallbackResponse("Volcano API key not configured");
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            // Use endpoint ID as model if provided, otherwise use model name
            String model = (endpointId != null && !endpointId.isEmpty()) ? endpointId : modelName;
            requestBody.put("model", model);

            List<Map<String, Object>> messages = new ArrayList<>();

            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                Map<String, Object> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", request.getSystemPrompt());
                messages.add(systemMsg);
            }

            if (request.getHistory() != null) {
                for (var msg : request.getHistory()) {
                    Map<String, Object> histMsg = new HashMap<>();
                    histMsg.put("role", "USER".equals(msg.getSenderType()) ? "user" : "assistant");
                    histMsg.put("content", msg.getContent());
                    messages.add(histMsg);
                }
            }

            // Add tool results if any
            if (request.getToolResults() != null && !request.getToolResults().isEmpty()) {
                for (ToolExecutionResult result : request.getToolResults()) {
                    messages.add(result.toMessage());
                }
            }

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", request.getContent());
            messages.add(userMsg);

            requestBody.put("messages", messages);

            // Add tools if specified
            if (request.getTools() != null && !request.getTools().isEmpty()) {
                requestBody.put("tools", convertToolsToMap(request.getTools()));
            }

            log.debug("Calling Volcano API with model/endpoint: {}", model);

            String response = webClient.post()
                .uri(apiBase + "/chat/completions")
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
            log.error("Volcano API call failed", e);
            return createFallbackResponse("Volcano API error: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> generateStream(AgentRequest request) {
        if (!isAvailable()) {
            return Flux.just("Volcano API key not configured");
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            String model = (endpointId != null && !endpointId.isEmpty()) ? endpointId : modelName;
            requestBody.put("model", model);
            requestBody.put("stream", true);

            List<Map<String, Object>> messages = new ArrayList<>();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                Map<String, Object> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", request.getSystemPrompt());
                messages.add(systemMsg);
            }

            // Add history messages
            if (request.getHistory() != null) {
                for (var msg : request.getHistory()) {
                    Map<String, Object> histMsg = new HashMap<>();
                    histMsg.put("role", "USER".equals(msg.getSenderType()) ? "user" : "assistant");
                    histMsg.put("content", msg.getContent());
                    messages.add(histMsg);
                }
            }

            // Add tool results if any
            if (request.getToolResults() != null && !request.getToolResults().isEmpty()) {
                for (ToolExecutionResult result : request.getToolResults()) {
                    messages.add(result.toMessage());
                }
            }

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", request.getContent());
            messages.add(userMsg);

            requestBody.put("messages", messages);

            // Add tools if specified
            if (request.getTools() != null && !request.getTools().isEmpty()) {
                requestBody.put("tools", convertToolsToMap(request.getTools()));
            }

            return webClient.post()
                .uri(apiBase + "/chat/completions")
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
        meta.setProviderCode("VOLCANO");
        meta.setProviderName("Volcano Engine (Doubao)");
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
                String content = "";
                boolean hasToolCalls = false;

                if (message != null) {
                    // Check for content
                    if (message.has("content") && !message.get("content").isNull()) {
                        content = message.get("content").asText();
                    }

                    // Check for tool_calls
                    JsonNode toolCallsNode = message.get("tool_calls");
                    if (toolCallsNode != null && toolCallsNode.isArray()) {
                        List<ToolCall> toolCalls = new ArrayList<>();
                        for (JsonNode tc : toolCallsNode) {
                            ToolCall toolCall = new ToolCall();
                            toolCall.setId(tc.has("id") ? tc.get("id").asText() : "call-" + System.currentTimeMillis());
                            toolCall.setType("function");

                            ToolCall.Function func = new ToolCall.Function();
                            func.setName(tc.has("function") ? tc.get("function").get("name").asText() : "");
                            func.setArguments(tc.has("function") ? tc.get("function").get("arguments").asText() : "{}");
                            toolCall.setFunction(func);

                            toolCalls.add(toolCall);
                        }
                        agentResponse.setToolCalls(toolCalls);
                        hasToolCalls = true;
                    }
                }

                agentResponse.setContent(content);
                agentResponse.setHasToolCalls(hasToolCalls);
                agentResponse.setFinishReason(choices.get(0).has("finish_reason") ? choices.get(0).get("finish_reason").asText() : "stop");

                extractCodeBlocks(content, agentResponse);
            } else {
                agentResponse.setContent("Unexpected response format: " + response);
                agentResponse.setFinishReason("error");
            }
        } catch (Exception e) {
            log.error("Failed to parse Volcano response", e);
            agentResponse.setContent("Failed to parse response: " + e.getMessage());
            agentResponse.setFinishReason("error");
        }

        return agentResponse;
    }

    private List<Map<String, Object>> convertToolsToMap(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("type", tool.getType());

            Map<String, Object> funcMap = new HashMap<>();
            funcMap.put("name", tool.getFunction().getName());
            funcMap.put("description", tool.getFunction().getDescription());

            if (tool.getFunction().getParameters() != null) {
                Map<String, Object> paramsMap = new HashMap<>();
                paramsMap.put("type", tool.getFunction().getParameters().getType());
                paramsMap.put("properties", convertPropertiesToMap(tool.getFunction().getParameters().getProperties()));
                if (tool.getFunction().getParameters().getRequired() != null) {
                    paramsMap.put("required", tool.getFunction().getParameters().getRequired());
                }
                funcMap.put("parameters", paramsMap);
            } else {
                funcMap.put("parameters", new HashMap<>());
            }

            toolMap.put("function", funcMap);
            result.add(toolMap);
        }
        return result;
    }

    private Map<String, Map<String, Object>> convertPropertiesToMap(Map<String, ToolDefinition.ParameterProperty> properties) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (properties == null) return result;

        for (Map.Entry<String, ToolDefinition.ParameterProperty> entry : properties.entrySet()) {
            Map<String, Object> propMap = new HashMap<>();
            propMap.put("type", entry.getValue().getType());
            if (entry.getValue().getDescription() != null) {
                propMap.put("description", entry.getValue().getDescription());
            }
            if (entry.getValue().getEnumValues() != null) {
                propMap.put("enum", Arrays.asList(entry.getValue().getEnumValues()));
            }
            result.put(entry.getKey(), propMap);
        }
        return result;
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
        response.setContent(reason + ". Please configure VOLCANO_API_KEY.");
        response.setFinishReason("fallback");
        return response;
    }
}
