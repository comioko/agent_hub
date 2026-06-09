# Agent Adapter 规范

## 作用

定义新 Agent Provider 接入的标准流程，确保多 Provider 接入的一致性和可扩展性。

## 接口定义

所有 Agent Provider 必须实现 `AgentAdapter` 接口：

```java
public interface AgentAdapter {
    /**
     * 同步生成回复
     * @param request 包含用户消息、上下文、历史记录
     * @return Agent 响应
     */
    AgentResponse generate(AgentRequest request);

    /**
     * 流式生成回复（用于 SSE）
     * @param request 包含用户消息、上下文、历史记录
     * @return 流式文本片段
     */
    Flux<String> generateStream(AgentRequest request);

    /**
     * 获取 Provider 元信息
     * @return Provider 名称、模型、可用性
     */
    ProviderMeta getProviderMeta();

    /**
     * 健康检查
     * @return 是否可用
     */
    boolean isAvailable();
}
```

## 请求/响应模型

### AgentRequest

```java
@Data
public class AgentRequest {
    private Long userId;              // 用户 ID
    private Long conversationId;       // 会话 ID
    private String content;            // 用户消息
    private String systemPrompt;       // Agent 系统提示词
    private List<Message> history;     // 最近 N 条历史消息
}
```

### AgentResponse

```java
@Data
@AllArgsConstructor
public class AgentResponse {
    private String content;            // 文本回复
    private String finishReason;       // 结束原因 (stop, length, etc.)
    private List<ArtifactBlock> blocks; // 产物卡片列表
}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArtifactBlock {
    private String type;               // CODE, DIFF, WEB_PREVIEW, etc.
    private String content;           // 内容
    private String language;          // 编程语言（code block 时用）
    private String metadata;          // 扩展信息 JSON
}
```

### ProviderMeta

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProviderMeta {
    private String providerCode;      // PROVIDER_CODE
    private String providerName;      // 显示名称
    private String modelName;        // 具体模型
    private boolean available;        // 是否可用
}
```

## 实现 Checklist

接入新 Provider 时，必须完成以下步骤：

### 1. 创建 Adapter 类

```java
package com.agenthub.adapter.{provider};

@Component
public class {Provider}Adapter implements AgentAdapter {

    @Value("${provider.api.key}")
    private String apiKey;

    @Value("${provider.api.base}")
    private String apiBase;

    private final WebClient webClient;

    public {Provider}Adapter() {
        this.webClient = WebClient.builder().build();
    }

    @Override
    public AgentResponse generate(AgentRequest request) {
        // 实现生成逻辑
    }

    @Override
    public Flux<String> generateStream(AgentRequest request) {
        // 实现流式生成
    }

    @Override
    public ProviderMeta getProviderMeta() {
        ProviderMeta meta = new ProviderMeta();
        meta.setProviderCode("PROVIDER_CODE");
        meta.setProviderName("Provider Name");
        meta.setModelName("model-name");
        meta.setAvailable(!apiKey.isEmpty());
        return meta;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
```

### 2. 注册到 AgentCore

```java
// AgentCore 构造方法中
public AgentCore(BuiltinAgentAdapter builtinAgentAdapter,
                 {Provider}Adapter providerAdapter) {
    adapters.put("BUILTIN", builtinAgentAdapter);
    adapters.put("PROVIDER_CODE", providerAdapter);
}
```

### 3. 数据库配置

```sql
-- 插入 Provider
INSERT INTO agent_provider (code, name, api_base, enabled)
VALUES ('PROVIDER_CODE', 'Provider Name', 'https://api.provider.com', TRUE);

-- 插入 Agent
INSERT INTO agent (code, name, provider, provider_model, system_prompt, enabled)
VALUES ('agent-code', 'Agent Name', 'PROVIDER_CODE', 'model-name', 'You are...', TRUE);
```

### 4. 环境变量配置

```bash
# .env 或环境变量
export PROVIDER_API_KEY=your-api-key
export PROVIDER_API_BASE=https://api.provider.com  # 可选
```

### 5. 单元测试

- [ ] generate() 方法测试
- [ ] generateStream() 方法测试（如果实现）
- [ ] isAvailable() 健康检查
- [ ] 错误处理（API 超时、限流、invalid key）
- [ ] Mock 模式切换测试

## Provider API 调用规范

### 必须遵循

1. **使用 WebClient**（非阻塞）
2. **超时设置**：60 秒
3. **重试策略**：最多 1 次
4. **错误映射**：外部错误 → `AgentException`

### 代码模板

```java
@Override
public AgentResponse generate(AgentRequest request) {
    try {
        // 1. 构建请求体
        Map<String, Object> body = buildRequestBody(request);

        // 2. 调用 API
        String response = webClient.post()
            .uri(apiBase + "/endpoint")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(60))
            .retry(1)
            .onErrorResume(e -> Mono.just(handleError(e)))
            .block();

        // 3. 解析响应
        return parseResponse(response);

    } catch (WebClientResponseException e) {
        throw new AgentException("API error: " + e.getStatusCode());
    } catch (Exception e) {
        throw new AgentException("Request failed: " + e.getMessage());
    }
}

private Map<String, Object> buildRequestBody(AgentRequest request) {
    Map<String, Object> body = new HashMap<>();
    // 根据 Provider 要求构建
    return body;
}

private AgentResponse parseResponse(String response) {
    // 解析 Provider 特定响应格式
    // 转换为统一的 AgentResponse
}
```

## 错误处理

```java
public class AgentException extends RuntimeException {
    private final int code;

    public AgentException(String message) {
        super(message);
        this.code = 3001; // Agent 调用失败
    }

    public AgentException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

### 错误场景处理

| 场景 | 处理方式 |
|------|----------|
| API Key 无效 | isAvailable() 返回 false |
| 请求超时 | 重试 1 次，仍失败抛 AgentException |
| 限流 (429) | 等待后重试，返回友好提示 |
| 服务端错误 (5xx) | 抛 AgentException |
| 网络错误 | 抛 AgentException，提示检查网络 |

## 流式生成 (SSE)

```java
@Override
public Flux<String> generateStream(AgentRequest request) {
    return webClient.post()
        .uri(apiBase + "/chat/stream")
        .header("Authorization", "Bearer " + apiKey)
        .bodyValue(buildRequestBody(request))
        .retrieve()
        .bodyToFlux(String.class)
        .timeout(Duration.ofSeconds(120))
        .map(this::extractContentFromStream);
}
```

## 扩展指南

### 添加新模型

1. 在 Provider API 文档确认模型 ID
2. 更新 Agent 表的 `provider_model` 字段
3. Adapter 的 `getProviderMeta()` 返回新模型名

### 添加新能力

如需支持工具调用、Function Calling：

```java
public class AgentRequest {
    // 新增字段
    private List<ToolDefinition> tools;
    private Map<String, Object> extraParams;
}
```
