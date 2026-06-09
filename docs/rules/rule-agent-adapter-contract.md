# Rule: Agent Adapter 规范

## 目的

统一 Agent 适配器实现标准。

## 接口契约

```java
public interface AgentAdapter {
    /**
     * 同步生成回复
     */
    AgentResponse generate(AgentRequest request);

    /**
     * 流式生成回复（用于 SSE）
     */
    Flux<String> generateStream(AgentRequest request);

    /**
     * 获取 Provider 元信息
     */
    ProviderMeta getProviderMeta();

    /**
     * 健康检查
     */
    boolean isAvailable();
}
```

## 实现要求

### generate()

```java
@Override
public AgentResponse generate(AgentRequest request) {
    // 1. 参数校验
    if (request.getContent() == null || request.getContent().isEmpty()) {
        throw new IllegalArgumentException("Content cannot be empty");
    }

    // 2. 检查可用性
    if (!isAvailable()) {
        return fallbackResponse("Provider not available");
    }

    try {
        // 3. 构建请求
        Map<String, Object> body = buildRequestBody(request);

        // 4. 调用 API
        String response = callApi(body);

        // 5. 解析响应
        return parseResponse(response);

    } catch (Exception e) {
        throw new AgentException("Generate failed: " + e.getMessage());
    }
}
```

### generateStream()

```java
@Override
public Flux<String> generateStream(AgentRequest request) {
    // 使用 WebClient 的 bodyToFlux 进行流式处理
    return webClient.post()
        .uri("/chat/stream")
        .bodyValue(buildRequestBody(request))
        .retrieve()
        .bodyToFlux(String.class)
        .map(this::extractContentFromStream)
        .timeout(Duration.ofSeconds(120));
}
```

### getProviderMeta()

```java
@Override
public ProviderMeta getProviderMeta() {
    ProviderMeta meta = new ProviderMeta();
    meta.setProviderCode("PROVIDER_CODE");
    meta.setProviderName("Provider Name");
    meta.setModelName("model-name");
    meta.setAvailable(isAvailable());
    return meta;
}
```

### isAvailable()

```java
@Override
public boolean isAvailable() {
    // 检查 API Key 是否配置
    return apiKey != null && !apiKey.isEmpty();
}
```

## 错误处理

### 错误分类

| 错误类型 | 处理方式 |
|----------|----------|
| API Key 无效 | isAvailable() 返回 false |
| 请求超时 | 重试 1 次，仍失败抛 AgentException |
| 限流 (429) | 等待后重试 |
| 服务端错误 (5xx) | 抛 AgentException |
| 网络错误 | 抛 AgentException |

### AgentException

```java
public class AgentException extends RuntimeException {
    private final int code = 3001;  // Agent 调用失败

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## API 调用规范

### WebClient 配置

```java
private final WebClient webClient;

public MyAdapter() {
    this.webClient = WebClient.builder()
        .baseUrl("https://api.provider.com")
        .defaultHeader("Content-Type", "application/json")
        .build();
}
```

### 超时设置

```java
.timeout(Duration.ofSeconds(60))  // 同步
.timeout(Duration.ofSeconds(120))  // 流式
```

### 重试策略

```java
.retry(1)  // 最多重试 1 次
```

## Fallback 响应

当 Provider 不可用时，返回友好提示：

```java
private AgentResponse fallbackResponse(String reason) {
    AgentResponse response = new AgentResponse();
    response.setContent("Agent is currently unavailable: " + reason +
        ". Please try again later.");
    response.setFinishReason("fallback");
    return response;
}
```

## 注册管理

Adapter 由 Spring 管理，但不标注 `@Service`：

```java
@Component  // 使用 @Component 而非 @Service
public class OpenAIAdapter implements AgentAdapter {
    // ...
}
```

统一由 AgentCore 注册：

```java
@Component
public class AgentCore {
    private final Map<String, AgentAdapter> adapters = new ConcurrentHashMap<>();

    @Autowired
    public AgentCore(BuiltinAgentAdapter builtin,
                     OpenAIAdapter openai,
                     ClaudeAdapter claude) {
        adapters.put("BUILTIN", builtin);
        adapters.put("OPENAI", openai);
        adapters.put("ANTHROPIC", claude);
    }
}
```

## 注意事项

1. **不要在 Adapter 中处理业务逻辑**
2. **不要直接访问数据库**
3. **不要抛出非 AgentException 的业务异常**
4. **始终设置超时**
5. **始终检查 API Key**
