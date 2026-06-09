package com.agenthub.adapter;

import com.agenthub.agent.AgentAdapter;
import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.agent.model.ProviderMeta;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BuiltinAgentAdapter implements AgentAdapter {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)?\\n([\\s\\S]*?)```");

    private final Map<String, String> builtinResponses = new HashMap<>();

    public BuiltinAgentAdapter() {
        builtinResponses.put("assistant", "I'm your AI assistant. I can help you with a variety of tasks including answering questions, writing, analysis, and more. How can I assist you today?");
        builtinResponses.put("coder", "I'm a coding specialist. I can help you write, debug, and understand code in various programming languages.");
        builtinResponses.put("reviewer", "I'm a code reviewer focused on quality, security, and best practices. I can analyze your code and provide constructive feedback.");
    }

    @Override
    public AgentResponse generate(AgentRequest request) {
        String content = request.getContent();
        String systemPrompt = request.getSystemPrompt();

        String response = generateResponse(content, systemPrompt, request.getHistory());

        AgentResponse agentResponse = new AgentResponse();
        agentResponse.setContent(response);
        agentResponse.setFinishReason("stop");

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);
        String remaining = response;
        int lastEnd = 0;

        while (matcher.find()) {
            remaining = remaining.substring(0, matcher.start()) + remaining.substring(matcher.end());
            String language = matcher.group(1);
            String code = matcher.group(2);

            if (language != null && !language.isEmpty()) {
                agentResponse.addCodeBlock(code.trim(), language);
            } else {
                agentResponse.addCodeBlock(code.trim(), "text");
            }
            lastEnd = matcher.end();
        }

        if (!agentResponse.getBlocks().isEmpty()) {
            String textPart = response.replaceAll("```(\\w+)?\\n[\\s\\S]*?```", "").trim();
            if (!textPart.isEmpty()) {
                agentResponse.setContent(textPart);
            } else {
                response = "Here is the code you requested:\n\n" + response;
            }
        }

        return agentResponse;
    }

    @Override
    public Flux<String> generateStream(AgentRequest request) {
        String response = generate(request).getContent();
        // Split into individual characters with small delay for effect
        String[] chars = response.split("");
        return Flux.fromArray(chars)
            .delayElements(java.time.Duration.ofMillis(10));
    }

    @Override
    public ProviderMeta getProviderMeta() {
        ProviderMeta meta = new ProviderMeta();
        meta.setProviderCode("BUILTIN");
        meta.setProviderName("Built-in Agent");
        meta.setModelName("builtin");
        meta.setAvailable(true);
        return meta;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private String generateResponse(String userContent, String systemPrompt, java.util.List<com.agenthub.model.entity.Message> history) {
        String lowerContent = userContent.toLowerCase();

        if (lowerContent.contains("hello") || lowerContent.contains("hi") || lowerContent.contains("hey")) {
            return "Hello! I'm ready to help you. What would you like to work on today?";
        }

        if (lowerContent.contains("code") || lowerContent.contains("function") || lowerContent.contains("implement")) {
            return generateCodingResponse(userContent);
        }

        if (lowerContent.contains("debug") || lowerContent.contains("error") || lowerContent.contains("bug")) {
            return generateDebugResponse(userContent);
        }

        if (lowerContent.contains("review") || lowerContent.contains("improve") || lowerContent.contains("refactor")) {
            return generateReviewResponse(userContent);
        }

        if (systemPrompt != null && systemPrompt.toLowerCase().contains("coder")) {
            return generateCodingResponse(userContent);
        }

        if (systemPrompt != null && systemPrompt.toLowerCase().contains("reviewer")) {
            return generateReviewResponse(userContent);
        }

        return generateGeneralResponse(userContent);
    }

    private String generateCodingResponse(String content) {
        String lowerContent = content.toLowerCase();

        if (lowerContent.contains("python") || lowerContent.contains("list") || lowerContent.contains("filter")) {
            return "Here's a Python function that demonstrates list operations:\n\n```python\ndef process_items(items, condition):\n    \"\"\"\n    Filter and process items based on a condition.\n\n    Args:\n        items: List of items to process\n        condition: A function that returns True for items to keep\n\n    Returns:\n        List of filtered and processed items\n    \"\"\"\n    result = []\n    for item in items:\n        if condition(item):\n            result.append(item)\n    return result\n\n\n# Example usage\nnumbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]\neven_numbers = process_items(numbers, lambda x: x % 2 == 0)\nprint(even_numbers)  # Output: [2, 4, 6, 8, 10]\n```\n\nThis implementation:\n- Uses a simple loop for clarity\n- Accepts any callable as the condition\n- Handles empty lists gracefully";
        }

        if (lowerContent.contains("java") || lowerContent.contains("class")) {
            return "Here's a Java class example:\n\n```java\npublic class DataProcessor<T> {\n    private List<T> data;\n\n    public DataProcessor() {\n        this.data = new ArrayList<>();\n    }\n\n    public void add(T item) {\n        data.add(item);\n    }\n\n    public List<T> getAll() {\n        return new ArrayList<>(data);\n    }\n\n    public Optional<T> findFirst(java.util.function.Predicate<T> predicate) {\n        return data.stream()\n            .filter(predicate)\n            .findFirst();\n    }\n}\n```\n\nKey design points:\n- Generic type `T` for flexibility\n- Defensive copying in `getAll()`\n- Stream API for filtering";
        }

        return "Here's a simple implementation approach:\n\n```python\ndef solution(data):\n    # Process the input data\n    result = []\n    for item in data:\n        processed = item.strip().lower()\n        if processed:\n            result.append(processed)\n    return result\n\n\n# Test\ntest_data = [\"Hello\", \"World\", \"  \", \"AgentHub\"]\nprint(solution(test_data))  # ['hello', 'world', 'agenthub']\n```";
    }

    private String generateDebugResponse(String content) {
        return "I'd be happy to help debug this. Here's a systematic approach:\n\n**Debugging Steps:**\n\n1. **Check the error message** - Look at the exact error type and line number\n2. **Verify inputs** - Ensure all parameters are in the correct format\n3. **Add logging** - Insert print statements to track variable values\n4. **Isolate the issue** - Comment out code to find the problematic section\n\n```python\n# Example debugging technique\ndef debug_value(name, value):\n    print(f\"DEBUG {name}: {value} (type: {type(value).__name__})\")\n\n# Use it like:\ndebug_value(\"user_input\", user_input)\nresult = process(user_input)\ndebug_value(\"result\", result)\n```\n\nCould you share the specific error message or code that's causing issues?";
    }

    private String generateReviewResponse(String content) {
        return "**Code Review Feedback:**\n\nHere's my analysis of the code:\n\n**Strengths:**\n- Clear function naming\n- Basic error handling in place\n- Readable indentation and style\n\n**Areas for Improvement:**\n\n1. **Add type hints** for better documentation:\n```python\ndef process_items(items: list) -> list:\n    ...\n```\n\n2. **Consider edge cases:**\n- Empty input lists\n- Null/None values\n- Very large inputs\n\n3. **Add docstrings** explaining the function purpose\n\n**Suggested Refactor:**\n\n```python\ndef process_items(items: list[str]) -> list[str]:\n    \"\"\"Filter and normalize a list of strings.\n\n    Args:\n        items: List of strings to process\n\n    Returns:\n        List of non-empty, lowercased strings\n    \"\"\"\n    if not items:\n        return []\n\n    return [item.strip().lower() for item in items if item.strip()]\n```\n\nWould you like me to elaborate on any of these points?";
    }

    private String generateGeneralResponse(String content) {
        return "Thank you for your message. Based on your input, here are my thoughts:\n\n" + content + "\n\nI can help you explore this topic further. Would you like me to:\n\n1. Provide more detailed explanations\n2. Show relevant code examples\n3. Break down the concepts step by step\n\nJust let me know how I can assist you best!";
    }
}
