# Orchestrator 规范

## 作用

定义群聊模式下任务拆解、分派、汇总的决策逻辑，确保多 Agent 协作的有序进行。

## 职责边界

### Orchestrator 负责

1. **解析 @mention**：从用户消息中识别被 @ 的 Agent
2. **判断调度需求**：决定是否需要 Orchestrator 介入
3. **拆解任务**：将用户请求拆分为子任务
4. **分派执行**：调用相应的 Agent 处理子任务
5. **汇总结果**：将多个 Agent 的响应合并为最终回复

### Orchestrator 不负责

- 复杂任务依赖图分析
- Agent 生命周期管理（由 AgentCore 负责）
- 会话状态持久化（由 Service 层负责）
- Agent 负载均衡
- 失败重试（由 Adapter 层负责）

## 工作流程

```
用户消息 (@agent1 @agent2)
        │
        ▼
┌───────────────────┐
│ GroupAgentHandler │  识别 @mention
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  Orchestrator     │  shouldOrchestrate()
└───────────────────┘
        │
   是否需要调度？
        │
   ┌────┴────┐
   │         │
  否         是
   │         │
   ▼         ▼
 直接调用    ┌───────────────────┐
 SingleAgent │  decompose()      │  拆解任务
             └───────────────────┘
                      │
                      ▼
             ┌───────────────────┐
             │  并行/串行分派    │
             └───────────────────┘
                      │
                      ▼
             ┌───────────────────┐
             │  aggregate()      │  汇总结果
             └───────────────────┘
```

## 接口定义

### Orchestrator 接口

```java
public interface Orchestrator {

    /**
     * 拆解用户消息，返回待执行任务列表
     * @param userMessage 用户原始消息
     * @param participants 会话参与者列表
     * @return 任务列表
     */
    List<AgentTask> decompose(String userMessage, List<Agent> participants);

    /**
     * 汇总子 Agent 结果
     * @param responses 各 Agent 响应列表
     * @return 汇总后的文本
     */
    String aggregate(List<AgentResponse> responses);

    /**
     * 判断是否需要 Orchestrator 介入
     * @param message 用户消息
     * @return true=需要调度，false=直接调用单 Agent
     */
    boolean shouldOrchestrate(Message message);
}
```

### AgentTask 模型

```java
@Data
public class AgentTask {
    private Long agentId;              // 目标 Agent ID
    private String taskDescription;    // 任务描述
    private Map<String, Object> context; // 扩展上下文
    private int priority;             // 优先级（可选）
}
```

## 简化实现策略

### MVP 策略（推荐起步）

```java
@Component
public class SimpleOrchestrator implements Orchestrator {

    @Override
    public boolean shouldOrchestrate(Message message) {
        // 群聊中包含 @mention 时启用
        String content = message.getContent();
        return content != null && content.contains("@");
    }

    @Override
    public List<AgentTask> decompose(String userMessage, List<Agent> participants) {
        List<AgentTask> tasks = new ArrayList<>();
        List<String> mentioned = extractMentions(userMessage);

        for (String name : mentioned) {
            Agent agent = participants.stream()
                .filter(a -> a.getName().equalsIgnoreCase(name.replace("@", "")))
                .findFirst()
                .orElse(null);

            if (agent != null) {
                AgentTask task = new AgentTask();
                task.setAgentId(agent.getId());
                task.setTaskDescription(cleanMessage(userMessage, name));
                tasks.add(task);
            }
        }
        return tasks;
    }

    @Override
    public String aggregate(List<AgentResponse> responses) {
        if (responses.isEmpty()) {
            return "No response from agents.";
        }
        if (responses.size() == 1) {
            return responses.get(0).getContent();
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < responses.size(); i++) {
            AgentResponse r = responses.get(i);
            sb.append("## Agent ").append(i + 1).append("\n\n");
            sb.append(r.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private List<String> extractMentions(String message) {
        // 简单实现：提取 @ 开头的单词
        List<String> mentions = new ArrayList<>();
        Pattern pattern = Pattern.compile("@(\\w+)");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }

    private String cleanMessage(String message, String mention) {
        // 移除 @mention 部分
        return message.replace("@" + mention, "").trim();
    }
}
```

### GroupAgentHandler 集成

```java
@Service
public class GroupAgentHandler {

    private final Orchestrator orchestrator;
    private final AgentCore agentCore;

    public AgentResponse handleGroupMessage(Message message, List<Agent> participants) {
        if (!orchestrator.shouldOrchestrate(message)) {
            return null; // 降级到单 Agent 处理
        }

        List<AgentTask> tasks = orchestrator.decompose(message.getContent(), participants);

        List<AgentResponse> responses = new ArrayList<>();
        for (AgentTask task : tasks) {
            Agent agent = findAgentById(task.getAgentId(), participants);
            if (agent != null) {
                AgentRequest request = buildAgentRequest(message, task);
                AgentResponse response = agentCore.generate(agent, request);
                responses.add(response);
            }
        }

        String aggregated = orchestrator.aggregate(responses);
        return buildAggregatedResponse(aggregated, responses);
    }
}
```

## P1 增强方向

当 MVP 稳定后，可考虑以下增强：

### 1. 意图识别

```java
public interface IntentClassifier {
    /**
     * 判断用户意图
     * @return INTENT_COLLABORATE, INTENT_SINGLE, INTENT_AMBIGUOUS
     */
    Intent classify(String message, List<Agent> participants);
}

public enum Intent {
    COLLABORATE,  // 需要多 Agent 协作
    SINGLE,       // 单 Agent 即可
    AMBIGUOUS     // 需要澄清
}
```

### 2. 任务优先级

```java
// 给任务分配优先级，实现部分并行
public interface TaskScheduler {
    List<List<AgentTask>> schedule(List<AgentTask> tasks);
}

// 示例：优先级高的任务先执行
public class PriorityScheduler implements TaskScheduler {
    @Override
    public List<List<AgentTask>> schedule(List<AgentTask> tasks) {
        // 返回分组，每组可以并行执行
    }
}
```

### 3. 结果冲突检测

```java
public interface ConflictDetector {
    /**
     * 检测多 Agent 结果是否有冲突
     * @return 冲突点列表
     */
    List<Conflict> detectConflicts(List<AgentResponse> responses);
}
```

## Orchestrator 注册

```java
@Configuration
public class AgentConfig {

    @Bean
    public Orchestrator orchestrator() {
        // MVP: 使用简单实现
        return new SimpleOrchestrator();

        // P1: 可切换到增强实现
        // return new EnhancedOrchestrator();
    }
}
```

## 测试策略

```java
@Test
public void testShouldOrchestrate_withMention() {
    Message message = new Message();
    message.setContent("Hello @agent1 @agent2");

    Orchestrator orchestrator = new SimpleOrchestrator();
    assertTrue(orchestrator.shouldOrchestrate(message));
}

@Test
public void testShouldOrchestrate_withoutMention() {
    Message message = new Message();
    message.setContent("Hello everyone");

    Orchestrator orchestrator = new SimpleOrchestrator();
    assertFalse(orchestrator.shouldOrchestrate(message));
}

@Test
public void testAggregate_multipleResponses() {
    List<AgentResponse> responses = Arrays.asList(
        new AgentResponse("Response 1", "stop", null),
        new AgentResponse("Response 2", "stop", null)
    );

    Orchestrator orchestrator = new SimpleOrchestrator();
    String aggregated = orchestrator.aggregate(responses);

    assertTrue(aggregated.contains("Response 1"));
    assertTrue(aggregated.contains("Response 2"));
}
```

## 注意事项

1. **不要过度设计**：MVP 阶段简单字符串拼接即可
2. **降级策略**：Orchestrator 失败时降级到单 Agent
3. **超时控制**：多 Agent 调用需要设置总超时
4. **日志记录**：记录任务拆解和执行过程，便于调试
