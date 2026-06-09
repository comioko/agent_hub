# Rule: Mock 与真实接入切换

## 目的

支持开发/测试/生产环境无缝切换。

## 配置方式

### application.yml

```yaml
agent:
  # 可选值: BUILTIN, OPENAI, ANTHROPIC
  provider: ${AGENT_PROVIDER:BUILTIN}
```

### 环境变量

```bash
# 开发环境（使用内置 Agent）
export AGENT_PROVIDER=BUILTIN

# 联调环境（使用 OpenAI）
export AGENT_PROVIDER=OPENAI
export OPENAI_API_KEY=sk-xxx

# 生产环境（使用 Claude）
export AGENT_PROVIDER=ANTHROPIC
export ANTHROPIC_API_KEY=sk-ant-xxx
```

## Provider 映射

```java
@Configuration
public class AgentConfig {

    @Value("${agent.provider:BUILTIN}")
    private String activeProvider;

    @Bean
    public AgentAdapter activeAgentAdapter(
            BuiltinAgentAdapter builtin,
            OpenAIAdapter openai,
            ClaudeAdapter claude) {

        return switch (activeProvider.toUpperCase()) {
            case "OPENAI" -> openai;
            case "ANTHROPIC" -> claude;
            default -> builtin;
        };
    }
}
```

## Agent 表配置

### 关联 Provider

```sql
INSERT INTO agent (code, name, provider, provider_model, enabled)
VALUES ('assistant', 'Assistant', 'BUILTIN', 'builtin', TRUE);

INSERT INTO agent (code, name, provider, provider_model, enabled)
VALUES ('gpt-4', 'GPT-4', 'OPENAI', 'gpt-4', TRUE);

INSERT INTO agent (code, name, provider, provider_model, enabled)
VALUES ('claude-3', 'Claude 3', 'ANTHROPIC', 'claude-3-haiku-20240307', TRUE);
```

### Agent 选择

```java
// 用户选择 Agent 时，关联的 provider 自动生效
public AgentResponse handleMessage(Long agentId, AgentRequest request) {
    Agent agent = agentMapper.selectById(agentId);
    // agent.getProvider() 返回 "BUILTIN" / "OPENAI" / "ANTHROPIC"

    AgentAdapter adapter = adapters.get(agent.getProvider());
    return adapter.generate(request);
}
```

## Mock Adapter 实现

### BuiltinAgentAdapter（MVP Mock）

```java
@Component
public class BuiltinAgentAdapter implements AgentAdapter {

    @Override
    public AgentResponse generate(AgentRequest request) {
        // 使用预设的响应模板
        AgentResponse response = new AgentResponse();
        response.setContent(generateMockResponse(request.getContent()));
        response.setFinishReason("stop");
        return response;
    }

    @Override
    public Flux<String> generateStream(AgentRequest request) {
        // 流式返回
        String content = generateMockResponse(request.getContent());
        return Flux.fromArray(content.split(""));
    }

    private String generateMockResponse(String input) {
        if (input.contains("hello") || input.contains("hi")) {
            return "Hello! I'm a mock agent. How can I help you?";
        }
        return "This is a mock response to: " + input;
    }
}
```

## 切换规则

| 环境 | Provider | 说明 |
|------|----------|------|
| 开发 | BUILTIN | 无需配置，快速启动 |
| 测试 | BUILTIN | 可预测的 Mock 响应 |
| 联调 | OPENAI/ANTHROPIC | 真实 API |
| 生产 | OPENAI/ANTHROPIC | 真实 API |

## 切换流程

### 1. 开发阶段

```bash
# 无需任何配置，使用默认 BUILTIN
mvn spring-boot:run
```

### 2. 联调阶段

```bash
# 配置 OpenAI
export AGENT_PROVIDER=OPENAI
export OPENAI_API_KEY=sk-xxx
mvn spring-boot:run
```

### 3. 生产阶段

```bash
# 使用环境变量或 K8s Secret
export AGENT_PROVIDER=${AGENT_PROVIDER}
export OPENAI_API_KEY=${OPENAI_API_KEY}
```

## 注意事项

1. **不要硬编码 API Key**：始终从环境变量读取
2. **优雅降级**：Provider 不可用时返回 fallback 响应
3. **日志记录**：记录使用的是哪个 Provider
4. **成本控制**：生产环境注意 API 调用频率

## 检查清单

- [ ] API Key 不在代码中硬编码
- [ ] 默认使用 BUILTIN
- [ ] 切换只需改环境变量
- [ ] 有 fallback 机制
