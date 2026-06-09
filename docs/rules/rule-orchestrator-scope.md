# Rule: Orchestrator 实现边界

## 目的

明确 Orchestrator 的职责范围，避免过度设计。

## 职责范围

### Orchestrator 应该做

1. **解析 @mention**
   ```java
   List<String> mentions = extractMentions(message.getContent());
   // 返回 ["agent1", "agent2"]
   ```

2. **判断是否需要调度**
   ```java
   boolean shouldOrchestrate = content.contains("@");
   ```

3. **拆解任务（简化版）**
   ```java
   // MVP：每个 @ 分配一个子任务
   List<AgentTask> tasks = new ArrayList<>();
   for (String mention : mentions) {
       AgentTask task = new AgentTask();
       task.setAgentId(findAgentId(mention));
       task.setTaskDescription(cleanMessage(content, mention));
       tasks.add(task);
   }
   ```

4. **并行/串行调用**
   ```java
   // 串行
   for (AgentTask task : tasks) {
       AgentResponse r = agentCore.generate(agent, request);
       responses.add(r);
   }

   // 并行（简化版）
   List<AgentResponse> responses = tasks.parallelStream()
       .map(task -> agentCore.generate(findAgent(task), request))
       .collect(Collectors.toList());
   ```

5. **汇总结果**
   ```java
   String result = String.join("\n\n", responses.stream()
       .map(AgentResponse::getContent)
       .collect(Collectors.toList()));
   ```

### Orchestrator 禁止做

| 禁止事项 | 原因 | 替代方案 |
|----------|------|----------|
| 直接访问数据库 | 违反模块边界 | Service 层处理 |
| 管理 Agent 生命周期 | AgentCore 负责 | - |
| 处理复杂依赖调度 | MVP 不需要 | P1 阶段扩展 |
| 实现重试逻辑 | Adapter 层处理 | - |
| 管理会话状态 | Service 层处理 | - |

## 实现约束

### MVP 阶段

```java
@Component
public class SimpleOrchestrator implements Orchestrator {

    @Override
    public boolean shouldOrchestrate(Message message) {
        // 简单策略：有 @ 就调度
        return message.getContent().contains("@");
    }

    @Override
    public List<AgentTask> decompose(String message, List<Agent> participants) {
        // 简单策略：每个 @ 一个任务
        // 不做意图识别、任务优先级
    }

    @Override
    public String aggregate(List<AgentResponse> responses) {
        // 简单策略：拼接所有响应
        return String.join("\n\n", ...);
    }
}
```

### 降级策略

```java
public AgentResponse handle(Message message) {
    if (!orchestrator.shouldOrchestrate(message)) {
        // 降级到单 Agent 处理
        return singleAgentHandler.handle(message);
    }

    try {
        return orchestrator.orchestrate(message);
    } catch (Exception e) {
        // Orchestrator 失败时降级
        log.error("Orchestrator failed, falling back", e);
        return singleAgentHandler.handle(message);
    }
}
```

## 调用链

```
用户消息
    │
    ▼
GroupAgentHandler
    │
    ├── 识别 @mention
    │
    ▼
Orchestrator.shouldOrchestrate()?
    │
    ├── YES ──► Orchestrator.decompose()
    │               │
    │               ▼
    │           AgentCore.generate() x N
    │               │
    │               ▼
    │           Orchestrator.aggregate()
    │               │
    └── NO ──► SingleAgentHandler.handle()
                    │
                    ▼
                AgentCore.generate()
```

## 日志要求

```java
log.info("Orchestrating message: id={}, mentions={}", message.getId(), mentions);
log.debug("Task decomposition: tasks={}", tasks);
log.info("Orchestration complete: messageId={}, responseCount={}",
    message.getId(), responses.size());
```

## 测试要求

```java
@Test
void shouldOrchestrate_withMention() {
    Message msg = new Message();
    msg.setContent("Hello @agent1 @agent2");
    assertTrue(orchestrator.shouldOrchestrate(msg));
}

@Test
void shouldOrchestrate_withoutMention() {
    Message msg = new Message();
    msg.setContent("Hello everyone");
    assertFalse(orchestrator.shouldOrchestrate(msg));
}

@Test
void decompose_twoMentions_twoTasks() {
    List<AgentTask> tasks = orchestrator.decompose(
        "@agent1 @agent2 hello",
        agents
    );
    assertEquals(2, tasks.size());
}
```

## 扩展方向（P1+）

1. **意图识别**：判断用户是真的要协作还是随口一说
2. **任务优先级**：某些任务可以并行，某些必须串行
3. **结果冲突检测**：多 Agent 返回不一致时处理
4. **动态 Agent 选择**：根据任务类型选择合适的 Agent
