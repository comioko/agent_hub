# Rule: 文件组织规范

## 目的

定义项目目录结构，保证代码组织一致性。

## 后端 Java 项目结构

```
com.agenthub/
├── AgentHubApplication.java      # 启动类
│
├── config/                       # 配置层
│   ├── SecurityConfig.java      # Spring Security 配置
│   ├── JwtConfig.java           # JWT 配置
│   ├── WebFluxConfig.java       # WebFlux/SSE 配置
│   └── JwtAuthenticationFilter.java
│
├── controller/                   # 控制器层
│   ├── AuthController.java      # 认证
│   ├── SessionController.java   # 会话管理
│   ├── MessageController.java   # 消息
│   └── AgentController.java     # Agent 管理
│
├── service/                      # 服务层
│   ├── AuthService.java
│   ├── SessionService.java
│   ├── MessageService.java
│   └── impl/                   # Service 实现（可选）
│       └── XxxServiceImpl.java
│
├── agent/                        # Agent 核心模块
│   ├── AgentCore.java          # Agent 能力入口
│   ├── AgentAdapter.java       # 适配器接口
│   ├── SingleAgentHandler.java  # 单 Agent 处理
│   ├── GroupAgentHandler.java  # 群聊处理
│   ├── Orchestrator.java       # 调度器接口
│   └── model/
│       ├── AgentRequest.java
│       ├── AgentResponse.java
│       ├── AgentTask.java
│       └── ProviderMeta.java
│
├── adapter/                      # Provider 适配器
│   ├── BuiltinAgentAdapter.java
│   ├── OpenAIAdapter.java
│   └── anthropic/
│       └── ClaudeAdapter.java
│
├── repository/                   # 数据访问层
│   ├── UserMapper.java
│   ├── ConversationMapper.java
│   ├── MessageMapper.java
│   ├── MessageBlockMapper.java
│   ├── AgentMapper.java
│   └── AgentProviderMapper.java
│
├── model/
│   ├── entity/                 # 数据库实体
│   │   ├── User.java
│   │   ├── Conversation.java
│   │   ├── Message.java
│   │   ├── MessageBlock.java
│   │   ├── Agent.java
│   │   ├── AgentProvider.java
│   │   └── ConversationParticipant.java
│   │
│   ├── dto/                    # 数据传输对象
│   │   ├── ApiResponse.java
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── AuthResponse.java
│   │   ├── SendMessageRequest.java
│   │   ├── CreateConversationRequest.java
│   │   ├── ConversationVO.java
│   │   └── MessageVO.java
│   │
│   └── enums/                  # 枚举类
│       ├── SenderType.java
│       ├── MessageType.java
│       ├── ConversationType.java
│       ├── ParticipantRole.java
│       └── BlockType.java
│
└── exception/                   # 异常处理
    ├── GlobalExceptionHandler.java
    └── BusinessException.java
```

## 后端资源结构

```
backend/src/main/
├── resources/
│   ├── application.yml          # Spring Boot 配置
│   ├── mapper/                  # MyBatis XML
│   │   └── *.xml
│   └── db/
│       ├── schema.sql          # 完整数据库 schema
│       └── migration/           # 增量迁移脚本
│           ├── V001__xxx.sql
│           └── V002__xxx.sql
```

## 前端项目结构

```
frontend/src/
├── main.jsx                    # 入口文件
├── App.jsx                     # 根组件
│
├── api/
│   └── agenthub.js            # API 调用封装
│
├── components/                 # 通用组件
│   ├── SessionList.jsx
│   ├── MessageList.jsx
│   ├── MessageItem.jsx
│   ├── MessageInput.jsx
│   ├── CodeBlock.jsx
│   └── ArtifactCards/
│       ├── DiffCard.jsx
│       ├── WebPreviewCard.jsx
│       └── DeployStatusCard.jsx
│
├── pages/                      # 页面组件
│   ├── LoginPage.jsx
│   ├── RegisterPage.jsx
│   └── ChatPage.jsx
│
├── stores/                     # Zustand stores
│   ├── authStore.js
│   ├── sessionStore.js
│   └── messageStore.js
│
├── hooks/                      # 自定义 hooks
│   ├── useSSE.js
│   └── useAuth.js
│
└── styles/
    └── index.css              # 全局样式 + Tailwind
```

## 模块内文件组织原则

1. **单一职责**：一个文件只负责一件事
2. **就近原则**：相关代码放在一起
3. **一致性**：同类文件使用相同组织方式

## 禁止

- 禁止在 Controller 中写业务逻辑
- 禁止在 Service 中直接操作 HTTP 请求/响应
- 禁止在 agent 模块中直接访问数据库
- 禁止在 adapter 中使用 @Service 注解（统一由 AgentCore 管理）
