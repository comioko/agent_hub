# AgentHub

Multi-Agent Collaboration Platform - A chat-based interface for collaborating with AI agents.

## 项目链接

- **GitHub**: https://github.com/comioko/agent_hub
- **演示视频**: [点击添加视频链接]()

---

## 产品概述

AgentHub 是一个多 Agent 协作平台，用户可以与多个 AI Agent 同时对话，让 Agent 之间相互协作完成复杂任务。

### 核心场景

| 场景 | 说明 |
|------|------|
| 单聊模式 | 与单个 AI Agent 进行一对一对话 |
| 群聊模式 | 多个 Agent 同时参与对话，并行处理任务 |
| 主管模式 | 主管 Agent 分析需求、分解任务、委托给子 Agent、汇总结果 |

---

## 功能列表

### 用户侧功能

| 功能 | 说明 |
|------|------|
| 用户注册/登录 | JWT 认证，支持密码登录 |
| 会话管理 | 创建、删除、置顶、归档会话 |
| 发送消息 | 支持 @mention 触发特定 Agent |
| 实时响应 | SSE 流式输出，边生成边显示 |
| 消息操作 | 回复、重新生成、置顶消息 |
| 上下文管理 | 自动/置顶/排除 三种上下文模式 |

### Agent 侧功能

| 功能 | 说明 |
|------|------|
| Agent 状态显示 | 实时显示执行状态（等待/执行中/已完成） |
| 并行执行 | 多个 Agent 同时响应消息 |
| 任务委托 | Agent 通过 @mention 委托任务给其他 Agent |
| 主管代理 | 特殊 Orchestrator Agent，负责协调其他 Agent |

---

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend                              │
│                   React 18 + Tailwind CSS                   │
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
│  │  - Message   │  │  - ToolExecutor│  │                │  │
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

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.2, MyBatis-Plus 3.5, MySQL 8.0 |
| 前端 | React 18, Vite, Tailwind CSS, Zustand, React Router |
| 实时通信 | SSE (Server-Sent Events) |
| 认证 | JWT |
| 并行处理 | CompletableFuture |

---

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0

### 后端启动

```bash
cd backend

# 创建数据库
mysql -u root -p < src/main/resources/db/schema.sql

# 配置环境变量
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=agenthub
export DB_USER=root
export DB_PASSWORD=your_password
export JWT_SECRET=your-secret-key

# 启动
./mvnw spring-boot:run
```

后端运行在 http://localhost:8080

### 前端启动

```bash
cd frontend

npm install
npm run dev
```

前端运行在 http://localhost:5173

---

## 核心模块

### 1. Agent 编排模块

**类**: `GroupAgentHandler`

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
```

### 2. Agent 适配器

统一接口 `AgentAdapter`，支持多种 Provider：

| 适配器 | Provider |
|--------|----------|
| DeepSeekAdapter | DeepSeek |
| OpenAIAdapter | OpenAI GPT |
| ClaudeAdapter | Anthropic Claude |
| MiniMaxAdapter | MiniMax |
| BuiltinAdapter | 内置 Agent |

### 3. SSE 实时推送

**事件类型**:

| 事件 | 说明 |
|------|------|
| `streaming` | Agent 正在生成内容 |
| `complete` | Agent 生成完成 |
| `message` | 新消息 |
| `tool_call` | 工具调用开始 |
| `tool_result` | 工具调用结果 |

---

## 数据库设计

### ER 图

```
┌─────────────┐       ┌──────────────────┐       ┌─────────────┐
│    user     │       │   conversation    │       │    agent    │
├─────────────┤       ├──────────────────┤       ├─────────────┤
│ id          │       │ id               │       │ id          │
│ username    │◄──┐   │ user_id          │       │ name        │
│ password    │   │   │ title            │   ┌──►│ description │
│ created_at  │   │   │ type             │   │   │ provider    │
└─────────────┘   │   │ created_at       │   │   │ model_id    │
                  │   └────────┬─────────┘   │   │ is_orchestr │
                  │            │           │   └─────────────┘
                  │            ▼           │
                  │   ┌──────────────────┐│
                  │   │conversation_    │◄─┘
                  │   │participant      │
                  │   ├──────────────────┤
                  │   │ conversation_id  │
                  │   │ agent_id         │
                  │   │ user_id          │
                  │   └──────────────────┘
                  │
                  │   ┌──────────────────┐
                  │   │     message      │
                  │   ├──────────────────┤
                  └──►│ conversation_id  │
                      │ sender_type      │
                      │ content          │
                      │ context_type     │
                      │ parent_id        │
                      └──────────────────┘
```

### 核心表

| 表名 | 说明 |
|------|------|
| user | 用户表 |
| conversation | 会话表 |
| conversation_participant | 会话参与者关联表 |
| message | 消息表 |
| agent | Agent 配置表 |
| message_block | 消息内容块表 |

---

## API 接口

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/login | 用户登录 |
| GET | /api/auth/me | 获取当前用户 |

### 会话

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/sessions | 列表 |
| POST | /api/sessions | 创建 |
| GET | /api/sessions/:id | 详情 |
| DELETE | /api/sessions/:id | 删除 |

### 消息

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/messages/conversation/:id | 获取会话消息 |
| POST | /api/messages | 发送消息 |
| PUT | /api/messages/:id/context | 更新上下文类型 |
| GET | /api/messages/subscribe | SSE 订阅 |

### Agent

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/agents | 列表 |
| GET | /api/agents/:id | 详情 |

---

## 开发指南

### 项目结构

```
agenthub/
├── backend/                    # Java Spring Boot 后端
│   └── src/main/java/com/agenthub/
│       ├── config/             # 配置类
│       ├── controller/         # REST 控制器
│       ├── service/           # 业务逻辑
│       ├── agent/             # Agent 核心逻辑
│       │   ├── GroupAgentHandler.java    # 多 Agent 编排
│       │   ├── AgentCore.java            # Agent 核心
│       │   ├── Orchestrator.java         # 任务编排器
│       │   └── SimpleOrchestrator.java   # 简单编排实现
│       ├── adapter/            # Agent Provider 适配器
│       ├── repository/        # 数据访问层
│       └── model/             # 实体和 DTO
├── frontend/                  # React 前端
│   └── src/
│       ├── api/               # API 调用
│       ├── components/        # UI 组件
│       ├── pages/             # 页面组件
│       ├── stores/            # Zustand 状态管理
│       └── hooks/             # 自定义 Hooks
└── docs/                      # 文档
```

### 添加新的 Agent Provider

1. 创建新的 Adapter 类实现 `AgentAdapter` 接口
2. 在 `AgentCore` 中注册适配器
3. 前端添加 Provider 选项

### 添加新的工具

1. 在 `ToolExecutor` 中注册工具定义
2. 实现工具执行逻辑
3. 在 Agent 的 System Prompt 中说明工具用法

---

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| DB_HOST | MySQL 主机 | localhost |
| DB_PORT | MySQL 端口 | 3306 |
| DB_NAME | 数据库名 | agenthub |
| DB_USER | 数据库用户 | root |
| DB_PASSWORD | 数据库密码 | - |
| JWT_SECRET | JWT 密钥 | - |
| OPENAI_API_KEY | OpenAI API Key | - |
| ANTHROPIC_API_KEY | Anthropic API Key | - |

---

## 部署

### 后端打包

```bash
cd backend
mvn clean package -DskipTests
java -jar target/agenthub-*.jar
```

### 前端打包

```bash
cd frontend
npm run build
# 输出在 dist/ 目录
```

### Nginx 配置示例

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

---

## 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1.0 | 2026-06-10 | 支持主管 Agent 模式、多 Agent 并行执行、上下文管理 |
| v1.0.0 | 2026-05-30 | 初始版本，单聊和群聊功能、SSE 实时响应 |

---

## License

MIT
