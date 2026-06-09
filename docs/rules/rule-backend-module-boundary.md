# Rule: 后端模块边界

## 目的

定义清晰的模块边界，防止代码耦合。

## 模块职责

```
┌─────────────────────────────────────────────────────────────┐
│                    Controller Layer                           │
│                                                              │
│  职责：                                                       │
│  - 接收 HTTP 请求                                              │
│  - 参数校验 (@Valid)                                          │
│  - 路由到 Service                                             │
│  - 返回响应                                                   │
│                                                              │
│  禁止：                                                       │
│  - 业务逻辑                                                   │
│  - 直接操作数据库                                             │
│  - 直接调用 Agent                                             │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                     Service Layer                            │
│                                                              │
│  职责：                                                       │
│  - 核心业务逻辑                                               │
│  - 事务管理 (@Transactional)                                 │
│  - 调用 Repository 读写数据                                  │
│  - 调用 Agent 模块生成响应                                    │
│                                                              │
│  禁止：                                                       │
│  - HTTP 请求/响应处理                                         │
│  - 直接操作数据库（通过 Repository）                          │
└─────────────────────────────────────────────────────────────┘
           │                                       │
┌──────────┴──────────┐              ┌────────────┴────────────┐
│  Repository Layer   │              │     Agent Module         │
│                      │              │                          │
│  职责：               │              │  职责：                   │
│  - MyBatis CRUD     │              │  - Agent 调用            │
│  - 数据访问          │              │  - Provider 适配        │
│  - 简单查询          │              │  - Orchestrator          │
│                      │              │                          │
│  禁止：               │              │  禁止：                   │
│  - 业务逻辑          │              │  - 直接访问数据库         │
│  - 复杂计算          │              │  - 业务逻辑               │
└─────────────────────┘              └──────────────────────────┘
```

## 跨模块调用规则

### 允许的调用链

```
Controller → Service → Repository
Controller → Service → AgentCore → AgentAdapter
Service → Service (同层调用)
```

### 禁止的调用链

```
Controller → Repository           # 禁止！绕过 Service
Service → Agent → Repository      # Agent 禁止访问数据库
Controller → AgentAdapter         # 禁止！绕过 AgentCore
```

## 模块接口

### Service 对外接口

```java
// Service 只通过方法签名对外暴露
public interface MessageService {
    MessageVO sendMessage(SendMessageRequest request, User sender);
    List<MessageVO> getConversationMessages(Long conversationId, User currentUser);
    SseEmitter subscribe(Long userId);
}
```

### Agent 模块对外接口

```java
// AgentCore 是 Agent 模块的唯一入口
public interface AgentCore {
    AgentResponse generate(Agent agent, AgentRequest request);
}
```

## 具体示例

### 正确实现

```java
// Controller - 只做请求处理
@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<ApiResponse<MessageVO>> send(
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal User currentUser) {
        MessageVO result = messageService.sendMessage(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}

// Service - 处理业务逻辑
@Service
public class MessageService {
    private final MessageMapper messageMapper;
    private final AgentCore agentCore;

    @Transactional
    public MessageVO sendMessage(SendMessageRequest request, User sender) {
        // 1. 业务校验
        validateConversation(request.getConversationId(), sender);

        // 2. 保存消息
        Message message = saveMessage(request, sender);

        // 3. 调用 Agent
        AgentResponse response = agentCore.generate(agent, request);

        // 4. 返回结果
        return buildMessageVO(message);
    }
}
```

### 错误实现（禁止）

```java
// 错误：Controller 直接操作数据库
@PostMapping
public ResponseEntity<ApiResponse<MessageVO>> send(@RequestBody ...) {
    messageMapper.insert(message);  // 禁止！
    return ResponseEntity.ok(ApiResponse.success(messageVO));
}

// 错误：Service 直接处理 HTTP
@Service
public class MessageService {
    public void process() {
        HttpRequest request = ...;  // 禁止！
    }
}

// 错误：Agent 模块访问数据库
@Component
public class ClaudeAdapter {
    public AgentResponse generate(AgentRequest request) {
        messageMapper.selectById(...);  // 禁止！
    }
}
```

## 模块依赖图

```
Controller ──────► Service ──────► Repository
                        │
                        │
                        ▼
                   AgentCore ──────► AgentAdapter
                        │
                        ▼
                   Orchestrator
```

## 原则

1. **单向依赖**：Controller → Service → Repository/Agent
2. **接口隔离**：模块间通过接口通信
3. **不循环依赖**：A 不能依赖 B 同时 B 依赖 A
