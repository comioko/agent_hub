# skill-orchestrator-dev.md

## 概述

本文档描述 Orchestrator（编排器）的开发规范，用于多Agent任务调度和结果聚合。

Orchestrator是AgentHub的核心差异化能力之一，实现用户在群聊中@多个Agent，由系统自动拆解任务并分派给合适的Agent依次执行。

---

## 适用场景

- 群聊中@多个Agent协同工作
- 任务需要多个专业领域Agent分工处理
- Orchestrator自动根据能力标签选择合适Agent
- 单Agent无法完成复杂任务时的任务拆解

---

## 调度模式

| 模式 | 说明 | 触发条件 |
|---|---|---|
| DIRECT | 用户明确@单个Agent，直接分派 | 消息中只@一个Agent |
| MULTI_AGENT | 用户@多个Agent，按顺序依次调用 | 消息中@多个Agent |
| AUTO_ROUTE | 用户未@，由Orchestrator根据能力选择 | 无@，但有多个Agent可用 |
| FALLBACK | Agent失败后使用备用Agent | 子Agent调用失败 |

---

## 输入输出

### 输入

```java
public class OrchestrationRequest {
    String userMessage;              // 用户原始消息
    List<Agent> availableAgents;     // 当前会话可用的Agent列表
    List<Long> mentionedAgentIds;    // 被@的Agent ID列表
    List<Message> conversationHistory; // 会话历史
    Map<String, String> context;     // 额外上下文
}
```

### 输出

```java
public class OrchestrationResult {
    List<SubTaskResult> subTasks;    // 各Agent执行结果
    String summary;                   // 聚合总结（可选）
    String status;                    // COMPLETED / PARTIAL_FAILED / FAILED
}
```

### 子任务结果

```java
public class SubTaskResult {
    Long agentId;                    // Agent ID
    String agentName;                 // Agent名称
    String response;                  // Agent回复内容
    List<ArtifactBlock> artifacts;    // 生成的Artifact
    boolean success;                  // 是否成功
    String errorMessage;              // 错误信息（如有）
}
```

---

## 调度流程

```mermaid
flowchart TD
    START[用户发送@消息] --> PARSE[解析@Agent列表]
    PARSE --> MODE{调度模式}
    MODE -->|DIRECT| SINGLE[调用单个Agent]
    MODE -->|MULTI_AGENT| PARALLEL{并行还是串行?}
    MODE -->|AUTO_ROUTE| SELECT[根据能力标签选择]
    PARALLEL -->|串行| SEQ[依次调用多个Agent]
    PARALLEL -->|并行| PAR[并行调用多个Agent]
    SINGLE --> RESULT[收集结果]
    SEQ --> RESULT
    PAR --> RESULT
    SELECT --> RESULT
    RESULT --> AGGREGATE[聚合结果]
    AGGREGATE --> SSE[SSE推送消息]
    SSE --> END[前端渲染]
```

---

## MVP实现策略

第一版不做复杂LLM Planner，只做规则调度。

### 规则调度优先级

1. 如果消息中出现 `@AgentName`，调用对应Agent
2. 如果有多个@，按出现顺序依次调用
3. 如果没有@，调用主Agent（会话创建时绑定的Agent）
4. 工具调用结果自动追加到下一轮上下文

### 简化实现

```java
@Service
public class SimpleOrchestrator {

    public OrchestrationResult orchestrate(OrchestrationRequest request) {
        List<Agent> targetAgents = resolveTargetAgents(request);

        List<SubTaskResult> results = new ArrayList<>();

        for (Agent agent : targetAgents) {
            // 构建子任务请求
            AgentRequest subRequest = buildSubRequest(request, agent, results);

            // 调用Agent
            AgentResponse response = agentCore.generate(agent, subRequest);

            // 收集结果
            results.add(SubTaskResult.builder()
                .agentId(agent.getId())
                .agentName(agent.getName())
                .response(response.getContent())
                .artifacts(response.getArtifacts())
                .success(response.isSuccess())
                .build());
        }

        return OrchestrationResult.builder()
            .subTasks(results)
            .status(determineStatus(results))
            .build();
    }

    private List<Agent> resolveTargetAgents(OrchestrationRequest request) {
        // 解析@的Agent
        if (!request.getMentionedAgentIds().isEmpty()) {
            return request.getAvailableAgents().stream()
                .filter(a -> request.getMentionedAgentIds().contains(a.getId()))
                .collect(Collectors.toList());
        }
        // 无@，返回主Agent
        return Collections.singletonList(
            request.getAvailableAgents().get(0)
        );
    }
}
```

---

## 消息@解析

### 前端@处理

```javascript
// MessageInput组件
const handleMention = (agent) => {
  const mentionText = `@${agent.name} `
  setInputValue(prev => prev + mentionText)
  setShowAgentPicker(false)
}

// 发送消息时，@信息作为mentions字段
const messageData = {
  content: inputValue,
  mentions: selectedAgents.map(a => ({
    agentId: a.id,
    agentCode: a.code,
    displayName: a.name
  }))
}
```

### 后端@解析

```java
public class MentionParser {

    public List<Long> parseMentionedAgentIds(String content, List<Agent> availableAgents) {
        List<Long> mentioned = new ArrayList<>();

        for (Agent agent : availableAgents) {
            String mentionPattern = "@" + agent.getName();
            if (content.contains(mentionPattern)) {
                mentioned.add(agent.getId());
            }
        }

        return mentioned;
    }
}
```

---

## 多Agent依次调用

### 串行调用模式

当用户@多个Agent时，按顺序依次调用，每个Agent的回复作为下一个Agent的上下文：

```java
public OrchestrationResult orchestrateMultiAgent(OrchestrationRequest request) {
    List<Agent> agents = resolveAgents(request);
    List<SubTaskResult> results = new ArrayList<>();
    StringBuilder contextBuilder = new StringBuilder();

    for (Agent agent : agents) {
        // 构建包含历史上下文的请求
        AgentRequest subRequest = buildRequestWithContext(request, agent, contextBuilder.toString());

        // 调用Agent
        AgentResponse response = agentCore.generateStream(agent, subRequest);

        // 收集结果
        String agentResponse = accumulateResponse(response);
        results.add(createSubTaskResult(agent, agentResponse));

        // 追加到上下文
        contextBuilder.append("\n\n")
            .append(agent.getName())
            .append(" 回复: ")
            .append(agentResponse);
    }

    return buildOrchestrationResult(results);
}
```

### 流式输出处理

多Agent串行调用时，每个Agent的响应都需要流式推送到前端：

```java
public void orchestrateWithStreaming(OrchestrationRequest request, SseEmitter emitter) {
    List<Agent> agents = resolveAgents(request);

    for (Agent agent : agents) {
        // 发送当前Agent开始事件
        sendAgentStartEvent(emitter, agent);

        // 流式调用
        Flux<String> stream = agentCore.generateStream(agent, request);
        StringBuilder fullResponse = new StringBuilder();

        stream.subscribe(
            chunk -> {
                fullResponse.append(chunk);
                sendStreamingEvent(emitter, agent, fullResponse.toString());
            },
            error -> {
                sendErrorEvent(emitter, agent, error.getMessage());
            },
            () -> {
                // Agent完成，发送完成事件
                sendAgentCompleteEvent(emitter, agent, fullResponse.toString());
            }
        );
    }
}
```

---

## 结果聚合

### 简单聚合策略

多Agent回复直接拼接：

```java
public String aggregateResults(List<SubTaskResult> results) {
    StringBuilder aggregated = new StringBuilder();

    for (SubTaskResult result : results) {
        aggregated.append("**")
            .append(result.getAgentName())
            .append(":**\n")
            .append(result.getResponse())
            .append("\n\n");
    }

    return aggregated.toString();
}
```

### 智能聚合（未来扩展）

使用LLM进行结果总结和去重：

```java
// 未来扩展：使用单独的聚合Agent
public String intelligentAggregate(List<SubTaskResult> results) {
    String prompt = buildAggregationPrompt(results);
    AgentResponse summary = llmAgent.generate(
        AgentRequest.builder().content(prompt).build()
    );
    return summary.getContent();
}
```

---

## Agent能力标签匹配

### 能力标签定义

```json
{
  "capabilityTags": ["code-generation", "code-review", "debugging", "documentation"]
}
```

### 自动选择逻辑

```java
public Agent selectAgentByCapability(String task, List<Agent> availableAgents) {
    // 简单匹配：按关键词选择
    for (Agent agent : availableAgents) {
        List<String> tags = agent.getCapabilityTags();
        if (tags == null) continue;

        if (task.contains("生成") && tags.contains("code-generation")) {
            return agent;
        }
        if (task.contains("检查") || task.contains("review") && tags.contains("code-review")) {
            return agent;
        }
        if (task.contains("调试") || task.contains("debug") && tags.contains("debugging")) {
            return agent;
        }
    }

    // 默认返回第一个
    return availableAgents.get(0);
}
```

---

## SSE事件格式

### 多Agent场景的SSE事件

```javascript
// 事件流
event: agent_start
data: {"agentId": 1, "agentName": "Claude Code"}

event: streaming
data: {"agentId": 1, "content": "我来帮你..."}

event: agent_complete
data: {"agentId": 1, "content": "完整的回复内容", "artifacts": [...]}

event: agent_start
data: {"agentId": 2, "agentName": "Codex"}

event: streaming
data: {"agentId": 2, "content": "我检查了代码..."}

event: agent_complete
data: {"agentId": 2, "content": "Codex的完整回复", "artifacts": [...]}

event: orchestration_complete
data: {"status": "COMPLETED", "agentCount": 2}
```

---

## 前端处理多Agent消息

### 消息列表展示

```javascript
// 每个Agent的消息作为独立message展示
// agentId区分来源
const messages = [
  { id: 1, senderType: 'USER', content: '@Claude @Codex 帮我完成这个任务' },
  { id: 2, senderType: 'AGENT', agentId: 1, agentName: 'Claude Code', content: '我来生成代码...' },
  { id: 3, senderType: 'AGENT', agentId: 2, agentName: 'Codex', content: '我来检查代码...' }
]
```

### 滚动策略

多Agent场景下，每个Agent开始时定位消息到顶部：

```javascript
if (type === 'agent_start') {
  // 新Agent开始，滚动到其消息位置
  scrollToMessage(data.agentId)
  setCurrentStreamingAgent(data.agentId)
}
```

---

## 错误处理

### Agent调用失败

```java
// 单个Agent失败不影响其他Agent
try {
    AgentResponse response = agentCore.generate(agent, request);
    results.add(createSuccessResult(agent, response));
} catch (Exception e) {
    log.error("Agent {} failed", agent.getName(), e);
    results.add(createFailureResult(agent, e.getMessage()));
}

// 最终状态
String status = results.stream().allMatch(SubTaskResult::isSuccess)
    ? "COMPLETED"
    : "PARTIAL_FAILED";
```

### 重试机制

```java
// 失败后自动重试一次
for (int retry = 0; retry < 2; retry++) {
    try {
        AgentResponse response = agentCore.generate(agent, request);
        return createSuccessResult(agent, response);
    } catch (Exception e) {
        if (retry == 1) {
            return createFailureResult(agent, e.getMessage());
        }
        log.warn("Retry {} for agent {}", retry + 1, agent.getName());
    }
}
```

---

## 验收检查清单

- [ ] 用户@单个Agent时，直接调用该Agent
- [ ] 用户@多个Agent时，按顺序依次调用
- [ ] 每个Agent的回复都能流式推送到前端
- [ ] 多Agent回复能正确展示在消息列表中
- [ ] Agent失败不影响其他Agent执行
- [ ] 失败重试机制正常工作
- [ ] 无@时能自动选择合适的Agent
- [ ] SSE事件格式正确，前端能正确解析
