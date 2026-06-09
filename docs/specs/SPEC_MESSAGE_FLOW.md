# 消息流转规范

## 作用

记录单聊/群聊场景下的完整数据流，作为前后端联调的依据和问题排查的参考。

## 单聊流程

```
┌────────┐                      ┌────────┐                    ┌────────┐
│ Client │                      │ Server │                    │  DB    │
└───┬────┘                      └───┬────┘                    └───┬────┘
    │                                │                           │
    │  POST /api/messages           │                           │
    │  {conversationId, content}    │                           │
    │ ─────────────────────────────>│                           │
    │                                │                           │
    │                                │  1. 保存用户消息          │
    │                                │  INSERT message          │
    │                                │ ─────────────────────────>│
    │                                │ <─────────────────────────│
    │                                │                           │
    │                                │  2. 查询会话参与者        │
    │                                │  SELECT participant       │
    │                                │ ─────────────────────────>│
    │                                │                           │
    │                                │  3. 获取 Agent 配置       │
    │                                │  SELECT agent             │
    │                                │ ─────────────────────────>│
    │                                │                           │
    │                                │  4. 调用 Agent            │
    │                                │  AgentAdapter.generate()  │
    │                                │ ─────────────────────────>│
    │                                │ <─────────────────────────│
    │                                │                           │
    │                                │  5. 保存 Agent 响应       │
    │                                │  INSERT message           │
    │                                │ ─────────────────────────>│
    │                                │ <─────────────────────────│
    │                                │                           │
    │                                │  6. 保存 Artifact blocks  │
    │                                │  INSERT message_block    │
    │                                │ ─────────────────────────>│
    │                                │ <─────────────────────────│
    │                                │                           │
    │  201 Created                   │
    │  {userMessage}                │
    │ <─────────────────────────────│                           │
    │                                │                           │
    │                                │  7. SSE 推送响应          │
    │  SSE: message                 │
    │  {agentMessage}               │
    │ <─────────────────────────────│                           │
```

### 时序说明

1. **用户发送消息**：POST 到后端
2. **保存用户消息**：先落库，保证消息不丢失
3. **查询参与者**：确定会话关联的 Agent
4. **调用 Agent**：使用选定的 Adapter
5. **保存 Agent 响应**：包括文本和 blocks
6. **返回用户消息**：告诉前端用户消息已接收
7. **SSE 推送**：Agent 响应通过 SSE 推送到前端

## 群聊流程（简化版）

```
用户消息: "@agent1 @agent2 帮我写代码"
        │
        ▼
GroupAgentHandler 识别 @agent1, @agent2
        │
        ▼
Orchestrator.shouldOrchestrate() = true
        │
        ▼
Orchestrator.decompose() 拆解任务
        │
        ├── Task1: agent1 → "帮我写代码" (去除 @agent2)
        └── Task2: agent2 → "帮我写代码" (去除 @agent1)
        │
        ▼
并行调用 AgentAdapter
        │
        ├──> Agent1.generate(task1) ──> Response1
        └──> Agent2.generate(task2) ──> Response2
        │
        ▼
Orchestrator.aggregate() 汇总结果
        │
        ▼
保存一条汇总消息 + 多个 block
        │
        ▼
SSE 推送 (agentMessage)
```

## SSE 连接管理

### 连接建立

```
前端: new EventSource('/api/messages/subscribe', {
        headers: { Authorization: 'Bearer ' + token }
      })
        │
        ▼
后端: MessageService.subscribe(userId)
        │
        ▼
返回 SseEmitter，并保存到 Map<Long, SseEmitter>
```

### 消息推送

```java
// 伪代码
SseEmitter emitter = activeEmitters.get(userId);
if (emitter != null) {
    emitter.send(SseEmitter.event()
        .name("message")
        .data(messageVO));
}
```

### 重连机制

```
SSE 断开 (网络/服务端重启)
        │
        ▼
前端 EventSource 自动重连
        │
        ▼
后端创建新的 SseEmitter
        │
        ▼
恢复消息推送
```

### 前端重连实现

```javascript
// useSSE.js
const connect = () => {
  const eventSource = new EventSource('/api/messages/subscribe', {
    headers: { Authorization: `Bearer ${token}` }
  })

  eventSource.onerror = () => {
    eventSource.close()
    // 指数退避重连，最多 3 次
    if (reconnectAttempts < 3) {
      setTimeout(connect, Math.pow(2, reconnectAttempts) * 1000)
      reconnectAttempts++
    }
  }
}
```

## 数据转换规则

### Message → MessageVO

```java
// 前端展示需要的信息
MessageVO vo = new MessageVO();
vo.setId(message.getId());
vo.setConversationId(message.getConversationId());
vo.setSenderType(message.getSenderType());  // USER 或 AGENT
vo.setSenderId(message.getSenderId());
vo.setContent(message.getContent());
vo.setMessageType(message.getMessageType());  // TEXT, ARTIFACT, SYSTEM
vo.setParentId(message.getParentId());
vo.setCreatedAt(message.getCreatedAt());

// 填充发送者信息
if ("USER".equals(message.getSenderType())) {
    vo.setSenderName("You");
} else {
    // Agent 时查询 Agent 表
    Agent agent = agentMapper.selectById(message.getSenderId());
    vo.setSenderName(agent.getName());
    vo.setSenderAvatar(agent.getAvatarUrl());
}
```

### Message → MessageBlockVO

```java
// 加载关联的 blocks
List<MessageBlock> blocks = messageBlockMapper.selectList(
    Wrappers.lambdaQuery(MessageBlock.class)
        .eq(MessageBlock::getMessageId, message.getId())
        .orderByAsc(MessageBlock::getSortOrder)
);

List<MessageBlockVO> blockVOs = blocks.stream().map(b -> {
    MessageBlockVO bv = new MessageBlockVO();
    bv.setId(b.getId());
    bv.setBlockType(b.getBlockType());
    bv.setContent(b.getContent());
    bv.setLanguage(b.getLanguage());
    bv.setMetadata(b.getMetadata());
    return bv;
}).collect(Collectors.toList());

vo.setBlocks(blockVOs);
```

## 错误处理

### 消息发送失败

```
客户端 POST 失败
        │
        ▼
前端显示发送失败状态
        │
        ▼
用户可重试
```

### Agent 调用超时

```
AgentAdapter.generate() 超时 (60s)
        │
        ▼
返回错误消息
"Agent response timeout. Please try again."
        │
        ▼
前端显示错误提示
```

### SSE 推送失败

```
SseEmitter.send() 抛出 IOException
        │
        ▼
emitter.complete()
        │
        ▼
从 activeEmitters 移除
        │
        ▼
前端自动重连
```

## 消息类型

| messageType | 说明 | 可见性 |
|-------------|------|--------|
| TEXT | 普通文本消息 | 用户可见 |
| ARTIFACT | 包含产物卡片的消息 | 用户可见 |
| SYSTEM | 系统消息（如 "Agent 已加入"） | 用户可见 |

## 上下文管理

### 历史消息范围

```java
// MessageService 获取历史
List<Message> getConversationHistory(Long conversationId, int limit) {
    return messageMapper.selectList(
        Wrappers.lambdaQuery(Message.class)
            .eq(Message::getConversationId, conversationId)
            .orderByDesc(Message::getCreatedAt)
            .last("LIMIT " + limit)
    );
}
```

### Token 限制

| Provider | 上下文限制 | 说明 |
|----------|-----------|------|
| Built-in | 20 条 | Java 内存处理 |
| OpenAI | ~4096 tokens | 按模型限制 |
| Claude | ~200k tokens | 按模型限制 |

### 超限处理

```
历史消息超过限制
        │
        ▼
截断最早的 N 条消息
        │
        ▼
保留最近的消息
        │
        ▼
传递给 Agent
```

## 性能考虑

1. **消息分页**：超过 100 条消息时前端分页加载
2. **SSE 连接限制**：每个用户一个连接
3. **消息缓存**：Redis 缓存活跃会话最近 N 条消息
4. **数据库索引**：`idx_conv_time (conversation_id, created_at)`
