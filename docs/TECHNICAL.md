# AgentHub 技术文档

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend                              │
│                   React + Tailwind CSS                       │
│                   ┌──────────────────┐                      │
│                   │   ChatPage       │                      │
│                   │   MessageList    │                      │
│                   │   AgentSidebar   │                      │
│                   └────────┬─────────┘                      │
└────────────────────────────┼────────────────────────────────┘
                             │ HTTP/SSE
┌────────────────────────────┼────────────────────────────────┐
│                        Backend                               │
│                   Spring Boot 3.2                            │
│  ┌─────────────┐  ┌────────┴───────┐  ┌────────────────┐  │
│  │  Controller  │  │    Service     │  │    Agent       │  │
│  │  - Auth      │  │  - Message     │  │  - GroupAgent  │  │
│  │  - Session   │  │  - Orchestrator│  │  - AgentCore   │  │
│  │  - Message   │  │  - ToolExecutor │  │                │  │
│  │  - Agent     │  │                │  │                │  │
│  └─────────────┘  └────────────────┘  └────────────────┘  │
│                            │                                 │
│                   ┌────────┴─────────┐                      │
│                   │   Agent Adapters │                      │
│  ┌────────────────┼─────────────────┼────────────────┐    │
│  │ DeepSeekAdapter│  OpenAIAdapter  │ ClaudeAdapter   │    │
│  └────────────────┴─────────────────┴────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │     MySQL 8.0   │
                    └─────────────────┘
```

## 技术栈

### 后端

| 技术 | 用途 |
|------|------|
| Java 17 | 编程语言 |
| Spring Boot 3.2 | Web 框架 |
| MyBatis-Plus 3.5 | ORM 框架 |
| MySQL 8.0 | 关系型数据库 |
| SSE | 服务端推送（实时响应） |
| JWT | 用户认证 |
| CompletableFuture | 并行任务执行 |

### 前端

| 技术 | 用途 |
|------|------|
| React 18 | UI 框架 |
| Vite | 构建工具 |
| Tailwind CSS | 样式框架 |
| Zustand | 状态管理 |
| React Router | 路由管理 |

## 核心模块

### 1. Agent 编排模块

**类**: `GroupAgentHandler`

负责多 Agent 协作的核心逻辑：

```
用户消息
    │
    ▼
┌─────────────────┐
│ 检测 @mention   │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
  有 @      无 @
    │         │
    ▼         ▼
┌───────────┐ ┌─────────────────┐
│ 调用被@   │ │ 检查主管 Agent  │
│ 的 Agent │ └────────┬────────┘
└───────────┘          │
              ┌─────────┴─────────┐
              │                  │
          有主管              无主管
              │                  │
              ▼                  ▼
        ┌──────────┐      ┌──────────┐
        │ 主管模式  │      │ 广播模式  │
        │ 委托任务  │      │ 所有Agent │
        └──────────┘      └──────────┘
              │
              ▼
        ┌─────────────────┐
        │ processHandoffs │
        │   (并行/串行)    │
        └─────────────────┘
```

**关键方法**:

- `handleGroupMessageStreaming()` - 主入口，处理群聊消息
- `callAgentSync()` - 同步调用单个 Agent
- `detectMentionsInText()` - 解析 @mention
- `processHandoffs()` - 处理 Agent 间任务委托
- `executeAgentsInParallel()` - 并行执行多个 Agent

### 2. Agent 适配器

**接口**: `AgentAdapter`

不同 AI Provider 的统一抽象：

```java
public interface AgentAdapter {
    Flux<String> generateStream(AgentRequest request);  // 流式生成
    AgentResponse generate(AgentRequest request);      // 非流式生成
    boolean isAvailable();                              // 可用性检查
    ProviderMeta getProviderMeta();                     // Provider 元信息
}
```

**实现类**:

| 适配器 | Provider |
|--------|----------|
| DeepSeekAdapter | DeepSeek |
| OpenAIAdapter | OpenAI GPT |
| ClaudeAdapter | Anthropic Claude |
| MiniMaxAdapter | MiniMax |
| BuiltinAdapter | 内置 Agent |

### 3. 消息服务

**类**: `MessageService`

核心功能：

- `sendMessage()` - 发送消息，触发 Agent 处理
- `getConversationHistory()` - 获取会话历史（智能上下文选择）
- `loadConversationHistory()` - 加载指定条数的历史消息

**智能上下文选择逻辑**:

```
1. 优先加载 PINNED（置顶）消息
2. 然后按最近时间加载 AUTO 消息
3. 跳过 EXCLUDED（排除）消息
4. 总数不超过 20 条
```

### 4. SSE 实时推送

**端点**: `GET /api/messages/subscribe`

**事件类型**:

| 事件 | 说明 |
|------|------|
| `streaming` | Agent 正在生成内容 |
| `complete` | Agent 生成完成 |
| `message` | 新消息 |
| `tool_call` | 工具调用开始 |
| `tool_result` | 工具调用结果 |

**事件格式**:

```json
// streaming 事件
{"id": 123, "content": "正在生成...", "agentId": 1}

// complete 事件
{"id": 123, "content": "完整回复", "agentId": 1, "senderType": "AGENT"}
```

## 数据库设计

### ER 图

```
┌─────────────┐       ┌──────────────────┐       ┌─────────────┐
│    user     │       │   conversation    │       │    agent    │
├─────────────┤       ├──────────────────┤       ├─────────────┤
│ id          │       │ id               │       │ id          │
│ username    │◄──┐   │ user_id          │       │ name        │
│ password    │   │   │ title            │   ┌──►│ description │
│ created_at  │   │   │ type (SINGLE/    │   │   │ provider    │
└─────────────┘   │   │         GROUP)   │   │   │ model_id    │
                  │   │ created_at       │   │   │ api_key     │
                  │   └────────┬─────────┘   │   │ is_enabled  │
                  │            │           │   │ is_orchest  │
                  │            │           │   └─────────────┘
                  │            ▼           │
                  │   ┌──────────────────┐   │
                  │   │conversation_    │◄──┘
                  │   │participant      │
                  │   ├──────────────────┤
                  │   │ id               │
                  │   │ conversation_id  │
                  │   │ agent_id         │
                  │   │ user_id          │
                  │   └──────────────────┘
                  │
                  │   ┌──────────────────┐
                  │   │     message      │
                  │   ├──────────────────┤
                  └──►│ id               │
                      │ conversation_id  │
                      │ sender_type      │
                      │ sender_id        │
                      │ content          │
                      │ context_type     │
                      │ context_priority │
                      │ parent_id        │
                      │ created_at       │
                      └────────┬─────────┘
                               │
                               ▼
                      ┌──────────────────┐
                      │   message_block  │
                      ├──────────────────┤
                      │ id               │
                      │ message_id       │
                      │ block_type       │
                      │ content          │
                      │ language         │
                      │ metadata         │
                      │ sort_order       │
                      └──────────────────┘
```

### 核心表

#### user
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(50) | 用户名 |
| password | VARCHAR(255) | BCrypt 加密密码 |
| created_at | DATETIME | 创建时间 |

#### conversation
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 所属用户 |
| title | VARCHAR(100) | 会话标题 |
| type | ENUM | SINGLE/GROUP |
| created_at | DATETIME | 创建时间 |

#### conversation_participant
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| conversation_id | BIGINT | 会话 ID |
| agent_id | BIGINT | Agent ID（群聊时） |
| user_id | BIGINT | 用户 ID |

#### message
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| conversation_id | BIGINT | 会话 ID |
| sender_type | ENUM | USER/AGENT/SYSTEM |
| sender_id | BIGINT | 发送者 ID |
| content | TEXT | 消息内容 |
| context_type | ENUM | AUTO/PINNED/EXCLUDED |
| context_priority | INT | 置顶优先级 |
| parent_id | BIGINT | 父消息 ID（回复） |

#### agent
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(100) | Agent 名称 |
| description | VARCHAR(500) | 描述 |
| provider | VARCHAR(50) | Provider 类型 |
| model_id | VARCHAR(100) | 模型 ID |
| api_key | VARCHAR(255) | API Key（加密） |
| enabled | BOOLEAN | 是否启用 |
| is_orchestrator | BOOLEAN | 是否为编排者 |

## API 接口

### 认证

#### POST /api/auth/register
```json
Request:
{"username": "test", "password": "123456"}

Response:
{"code": 200, "data": {"id": 1, "username": "test"}}
```

#### POST /api/auth/login
```json
Request:
{"username": "test", "password": "123456"}

Response:
{"code": 200, "data": {"token": "eyJ...", "user": {...}}}
```

### 会话

#### GET /api/sessions
```json
Response:
{"code": 200, "data": [
  {"id": 1, "title": "聊天1", "type": "GROUP", "agents": [...]}
]}
```

#### POST /api/sessions
```json
Request:
{"title": "新会话", "agentIds": [1, 2], "type": "GROUP"}

Response:
{"code": 200, "data": {"id": 1, "title": "新会话"}}
```

### 消息

#### GET /api/messages/conversation/:id
```json
Response:
{"code": 200, "data": [
  {"id": 1, "content": "你好", "senderType": "USER", ...}
]}
```

#### POST /api/messages
```json
Request:
{"conversationId": 1, "content": "帮我写代码"}

Response:
{"code": 200, "data": {"id": 2}}
```

#### GET /api/messages/subscribe
SSE 流，参考 SSE 事件格式

### Agent

#### GET /api/agents
```json
Response:
{"code": 200, "data": [
  {"id": 1, "name": "CodeAgent", "provider": "DEEPSEEK", ...}
]}
```

## 前端状态管理

使用 Zustand 进行状态管理：

### stores/authStore.js
- `user` - 当前用户信息
- `token` - JWT token
- `login()`, `logout()`, `register()`

### stores/sessionStore.js
- `sessions` - 会话列表
- `currentSession` - 当前会话
- `fetchSessions()`, `createSession()`, `setCurrentSession()`

### stores/messageStore.js
- `messages` - 消息列表
- `fetchMessages()`, `sendMessage()`, `addMessage()`
- `sendMessage()` 支持 SSE 流式更新

## 安全机制

### 认证
- JWT Token 存储在 localStorage
- 请求头携带 `Authorization: Bearer <token>`
- Token 过期时间 7 天

### 密码加密
- BCrypt 加密存储
- 强度因子 10

### CORS
- 开发环境允许 localhost:5173
- 生产环境需配置具体域名

### 数据隔离
- 用户只能访问自己的会话
- Agent 无法访问其他用户的会话

## 部署建议

### 环境要求
- JDK 17+
- Node.js 18+
- MySQL 8.0+
- 内存 4GB+

### 后端部署
```bash
cd backend
mvn clean package -DskipTests
java -jar target/agenthub-*.jar
```

### 前端部署
```bash
cd frontend
npm run build
# 输出在 dist/ 目录
```

### Nginx 配置
```nginx
server {
    listen 80;
    server_name agenthub.example.com;

    location / {
        root /var/www/agenthub/dist;
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
    }

    location /sse {
        proxy_pass http://localhost:8080;
        proxy_buffering off;
        proxy_cache off;
    }
}
```

## 扩展指南

### 添加新的 Agent Provider

1. 创建新的 Adapter 类实现 `AgentAdapter` 接口
2. 在 `AgentCore` 中注册适配器
3. 前端添加 Provider 选项

### 添加新的工具

1. 在 `ToolExecutor` 中注册工具定义
2. 实现工具执行逻辑
3. 在 Agent 的 System Prompt 中说明工具用法

### 扩展消息类型

1. 在 `MessageType` 枚举中添加新类型
2. 修改前端渲染逻辑
3. 更新数据库 schema（如需要）
