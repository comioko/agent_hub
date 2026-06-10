# AI 协作开发记录

> 记录与 AI (Claude Code) 协作开发 AgentHub 项目的过程和重要决策

## 项目背景

AgentHub 是一个多 Agent 协作平台，支持多个 AI Agent 同时参与对话、任务分解和协作完成复杂任务。

## 协作模式

### 1. 主要协作方式

- **实时编码协助**: AI 直接修改代码文件，处理 bug 修复、功能实现
- **架构设计讨论**: 使用 Plan Mode 进行方案设计
- **问题诊断**: 通过错误信息和日志定位问题根因
- **代码审查**: 检查代码逻辑和潜在问题

### 2. 沟通特点

- 使用中文进行日常交流
- AI 主动分析问题原因并修复
- 需要用户验证的功能会明确说明

## 已完成功能

### 核心功能

| 功能 | 状态 | 说明 |
|------|------|------|
| 多 Agent 群聊 | ✅ | 支持多个 Agent 同时参与对话 |
| 主管 Agent 模式 | ✅ | 主管分解任务并委托给子 Agent |
| SSE 流式输出 | ✅ | 实时显示 Agent 生成进度 |
| Agent 状态显示 | ✅ | 右侧边栏显示所有 Agent 状态 |
| @mention 触发 | ✅ | 通过 @ 触发特定 Agent |

### UI/UX 改进

| 功能 | 状态 | 说明 |
|------|------|------|
| 新会话弹窗 | ✅ | 新建群聊时正确弹出 |
| 日期显示修复 | ✅ | 自动填充创建时间 |
| 并行 Agent 显示 | ✅ | 多个 Agent 同时执行时正确显示 |
| 回复/重新生成 | ✅ | 消息操作按钮生效 |

### 基础设施

| 功能 | 状态 | 说明 |
|------|------|------|
| CORS 修复 | ✅ | 允许跨域登录 |
| Mybatis Plus 配置 | ✅ | 自动填充时间戳 |
| 数据库迁移 | ✅ | 添加上下文管理字段 |

## 重要技术决策

### 1. 并行 Agent 执行跟踪

**问题**: 多个 Agent 并行执行时，只有一个能正确显示状态

**解决方案**: 使用 `Map<agentId, content>` 跟踪所有活跃 Agent，而非单一变量

```javascript
const [activeAgents, setActiveAgents] = useState(new Map())
```

### 2. 主管 Agent 委托逻辑

**问题**: 主管 Agent 被加入 processedAgents 后，无法再委托给子 Agent

**解决方案**: 委托前从 processedAgents 移除主管

```java
processedAgents.remove(supervisor.getName().toLowerCase());
```

### 3. SSE 闭包问题

**问题**: useSSE hook 中的回调函数无法获取最新状态

**解决方案**: 使用 ref 存储回调函数

```javascript
const onMessageRef = useRef(onMessage)
onMessageRef.current = onMessage
```

## 遇到的问题与解决

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 登录 CORS 错误 | /error 路径未放行 | SecurityConfig 添加 permitAll |
| 日期显示 1970 | createdAt 未自动填充 | 创建 MybatisPlusConfig |
| 按钮无反应 | props 未正确传递 | 检查并修复组件间传参 |
| 回车后黑屏 | streamingMessageIdRef 丢失 | 恢复 ref 引用 |
| 并行显示异常 | 单变量无法跟踪多 Agent | 改用 Map 数据结构 |

## 数据库迁移

```sql
-- V6: 添加上下文管理字段
ALTER TABLE message ADD COLUMN context_type VARCHAR(20) DEFAULT 'AUTO';
ALTER TABLE message ADD COLUMN context_priority INT DEFAULT 0;
```

## Git 提交历史

| 提交 | 描述 |
|------|------|
| 831f1a7 | feat: supervisor agent delegation and multi-agent improvements |
| e15a34f | feat: Add message actions menu |
| 8f70bcc | feat: Multi-agent orchestration improvements and UI enhancements |
| 5c4d8a2 | Initial commit: AgentHub multi-agent collaboration platform |

## 协作心得

### AI 擅长的领域

- 快速代码修改和 bug 修复
- 代码库结构分析和搜索
- 提供多种解决方案并解释权衡
- 重复性任务的自动化

### 需要人工确认的

- 业务逻辑和需求理解
- 数据库结构变更
- 涉及外部服务的集成
- 安全和权限相关的决策

### 有效协作技巧

1. **明确描述问题**: 提供错误信息和上下文
2. **指定文件位置**: 帮助 AI 快速定位
3. **确认后再继续**: 重要决策先讨论再实现
4. **分阶段任务**: 复杂功能分步实现

---

*最后更新: 2026-06-10*
