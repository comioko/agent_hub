# Skill: 新 Agent Provider 接入

## 适用场景

接入新的 AI 平台（OpenAI、Claude、本地模型等）。

## 输入

- Provider API 文档
- 认证信息（API Key）
- 模型名称和端点

## 输出

完整的 AgentAdapter 实现，可正常调用新 Provider。

## 执行步骤

### 1. 确认 Provider 信息

收集以下信息：
- Provider 名称和代码（如 ANTHROPIC）
- API 端点（如 https://api.anthropic.com）
- 支持的模型列表
- 认证方式（API Key / OAuth）
- 请求格式
- 响应格式

### 2. 配置环境变量

```bash
# 在 .env 或系统环境变量中
export ANTHROPIC_API_KEY=sk-ant-xxxxx
export ANTHROPIC_API_BASE=https://api.anthropic.com  # 如需自定义
```

### 3. 创建 Adapter 类

```java
package com.agenthub.adapter.anthropic;

@Component
public class ClaudeAdapter implements AgentAdapter {

    @Value("${anthropic.api.key:}")
    private String apiKey;

    @Value("${anthropic.api.base:https://api.anthropic.com}")
    private String apiBase;

    private final WebClient webClient;

    public ClaudeAdapter() {
        this.webClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com")
            .build();
    }

    @Override
    public AgentResponse generate(AgentRequest request) {
        // 实现见下文
    }

    @Override
    public Flux<String> generateStream(AgentRequest request) {
        // 流式实现
    }

    @Override
    public ProviderMeta getProviderMeta() {
        return new ProviderMeta("ANTHROPIC", "Anthropic Claude",
            "claude-3-haiku-20240307", isAvailable());
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
```

### 4. 实现 generate() 方法

```java
@Override
public AgentResponse generate(AgentRequest request) {
    if (!isAvailable()) {
        return fallbackResponse("Provider not configured");
    }

    try {
        // 1. 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "claude-3-haiku-20240307");
        requestBody.put("max_tokens", 1024);

        // 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();

        // 添加历史消息
        if (request.getHistory() != null) {
            for (Message msg : request.getHistory()) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("role", "user".equals(msg.getSenderType()) ? "user" : "assistant");
                msgMap.put("content", msg.getContent());
                messages.add(msgMap);
            }
        }

        // 添加当前消息
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", request.getContent());
        messages.add(userMsg);

        requestBody.put("messages", messages);

        // 添加 system prompt
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            requestBody.put("system", request.getSystemPrompt());
        }

        // 2. 调用 API
        String response = webClient.post()
            .uri("/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(60))
            .retry(1)
            .block();

        // 3. 解析响应
        return parseResponse(response);

    } catch (Exception e) {
        throw new AgentException("Claude API call failed: " + e.getMessage());
    }
}

private AgentResponse parseResponse(String response) {
    // 解析 Provider 特定响应格式
    // 这里需要根据 Claude API 实际响应格式实现
    AgentResponse agentResponse = new AgentResponse();
    agentResponse.setContent("Parsed content from response");
    agentResponse.setFinishReason("stop");
    return agentResponse;
}
```

### 5. 注册到 AgentCore

```java
// AgentCore.java 构造方法中
public AgentCore(BuiltinAgentAdapter builtinAgentAdapter,
                 OpenAIAdapter openAIAdapter,
                 ClaudeAdapter claudeAdapter) {  // 添加新参数

    adapters.put("BUILTIN", builtinAgentAdapter);
    adapters.put("OPENAI", openAIAdapter);
    adapters.put("ANTHROPIC", claudeAdapter);  // 注册新 Adapter
}
```

### 6. 数据库配置

```sql
-- 插入 Provider
INSERT INTO agent_provider (code, name, api_base, enabled)
VALUES ('ANTHROPIC', 'Anthropic', 'https://api.anthropic.com', TRUE);

-- 插入 Agent
INSERT INTO agent (code, name, provider, provider_model, system_prompt, enabled)
VALUES ('claude-haiku', 'Claude Haiku', 'ANTHROPIC', 'claude-3-haiku-20240307',
        'You are a helpful AI assistant.', TRUE);
```

### 7. 测试验证

创建单元测试：
```java
@Test
public void testGenerate() {
    AgentRequest request = new AgentRequest();
    request.setContent("Hello");

    AgentAdapter adapter = new ClaudeAdapter();
    // 设置 mock API key

    AgentResponse response = adapter.generate(request);
    assertNotNull(response.getContent());
}

@Test
public void testIsAvailable_withKey() {
    AgentAdapter adapter = new ClaudeAdapter();
    // 模拟已设置 API key
    assertTrue(adapter.isAvailable());
}

@Test
public void testIsAvailable_withoutKey() {
    AgentAdapter adapter = new ClaudeAdapter();
    // 模拟未设置 API key
    assertFalse(adapter.isAvailable());
}
```

## 注意事项

1. **API Key 安全**：不得硬编码，使用 `@Value` 从环境变量读取
2. **超时处理**：必须设置 60s 超时
3. **重试策略**：最多重试 1 次
4. **错误转换**：外部错误转换为 AgentException
5. **fallback 响应**：Provider 不可用时返回友好提示

## 快速检查清单

- [ ] 环境变量已配置
- [ ] Adapter 类实现完整
- [ ] generate() 方法正确
- [ ] generateStream() 方法（如需要）
- [ ] getProviderMeta() 返回正确信息
- [ ] isAvailable() 逻辑正确
- [ ] 已注册到 AgentCore
- [ ] 数据库记录已插入
- [ ] 单元测试通过

## 常见 Provider 对接要点

### OpenAI

- 请求体：`{model, messages, temperature, max_tokens}`
- 认证：`Authorization: Bearer {api_key}`
- 响应： `{choices: [{message: {content}}]}`

### Anthropic

- 请求体：`{model, messages, max_tokens, system}`
- 认证：`x-api-key: {api_key}`
- 响应： `{content: [{text}]}`

### 本地模型 (Ollama)

- 请求体：`{model, messages}`
- 认证：无或 Basic Auth
- 响应： `{response}`
