# AgentHub 文档索引

AgentHub 是一个以 IM 聊天为核心交互范式的多 Agent 协作平台。用户可以像使用飞书或微信一样，与不同 AI Agent 单聊或群聊，并在聊天流中查看代码、Diff、网页预览、文件、部署状态等产物。

---

## 项目目标

| 优先级 | 目标 | 说明 |
|--------|------|------|
| P0 | 跑通 IM 单聊闭环 | 会话、消息、Agent 调用、SSE、历史上下文 |
| P1 | 跑通多 Agent 协作 | 群聊、@Agent、Orchestrator、Artifact 预览 |
| P2 | 增强产品体验 | Diff 应用、部署发布、版本历史、多端适配 |

---

## Specs - 规范定义

| 文档 | 说明 | 优先级 |
|------|------|--------|
| [SPEC_API.md](./specs/SPEC_API.md) | API 接口规范 | P0 |
| [SPEC_AGENT_ADAPTER.md](./specs/SPEC_AGENT_ADAPTER.md) | Agent 适配器规范 | P0 |
| [SPEC_MESSAGE_FLOW.md](./specs/SPEC_MESSAGE_FLOW.md) | 消息流转规范 | P0 |
| [SPEC_REQUIREMENT_COVERAGE.md](./specs/SPEC_REQUIREMENT_COVERAGE.md) | 需求覆盖矩阵 | P0 |
| [SPEC_ARCHITECTURE.md](./specs/SPEC_ARCHITECTURE.md) | 系统架构设计 | P0 |
| [SPEC_DATA_MODEL.md](./specs/SPEC_DATA_MODEL.md) | 数据模型总览 | P0 |
| [SPEC_ORCHESTRATOR.md](./specs/SPEC_ORCHESTRATOR.md) | Orchestrator 调度规范 | P1 |
| [SPEC_ARTIFACT.md](./specs/SPEC_ARTIFACT.md) | Artifact 卡片规范 | P1 |
| [SPEC_PRODUCT_REQUIREMENTS.md](./specs/SPEC_PRODUCT_REQUIREMENTS.md) | 产品需求总纲 | P1 |

## Skills - 操作指南

| 文档 | 适用场景 | 优先级 |
|------|----------|--------|
| [skill-message-flow-dev.md](./skills/skill-message-flow-dev.md) | 消息发送、持久化、SSE 推送链路 | P0 |
| [skill-orchestrator-dev.md](./skills/skill-orchestrator-dev.md) | 多 Agent 调度和结果聚合 | P0 |
| [skill-context-management.md](./skills/skill-context-management.md) | 历史上下文和 Pin 机制 | P1 |
| [skill-custom-agent-create.md](./skills/skill-custom-agent-create.md) | 用户自建 Agent | P1 |
| [skill-session-management.md](./skills/skill-session-management.md) | 会话列表、置顶、归档、搜索 | P1 |

## Skills - 操作指南

| 文档 | 适用场景 | 优先级 |
|------|----------|--------|
| [skill-message-flow-dev.md](./skills/skill-message-flow-dev.md) | 消息发送、持久化、SSE 推送链路 | P0 |
| [skill-orchestrator-dev.md](./skills/skill-orchestrator-dev.md) | 多 Agent 调度和结果聚合 | P0 |
| [skill-java-api-dev.md](./skills/skill-java-api-dev.md) | 新增 REST API | P0 |
| [skill-agent-provider-onboard.md](./skills/skill-agent-provider-onboard.md) | 接入新 Agent Provider | P0 |
| [skill-frontend-chat-ui.md](./skills/skill-frontend-chat-ui.md) | 开发聊天界面 | P0 |
| [skill-artifact-extension.md](./skills/skill-artifact-extension.md) | 新增产物卡片 | P1 |
| [skill-data-model-change.md](./skills/skill-data-model-change.md) | 数据库变更 | P0 |
| [skill-bug-investigation.md](./skills/skill-bug-investigation.md) | Bug 排查 | P0 |

## Rules - 开发规则

| 文档 | 说明 |
|------|------|
| [rule-naming-convention.md](./rules/rule-naming-convention.md) | 命名规范 |
| [rule-file-organization.md](./rules/rule-file-organization.md) | 文件组织 |
| [rule-api-design.md](./rules/rule-api-design.md) | API 设计规则 |
| [rule-backend-module-boundary.md](./rules/rule-backend-module-boundary.md) | 后端模块边界 |
| [rule-db-migration.md](./rules/rule-db-migration.md) | 数据变更规则 |
| [rule-ui-component.md](./rules/rule-ui-component.md) | UI 组件规范 |
| [rule-agent-adapter-contract.md](./rules/rule-agent-adapter-contract.md) | Adapter 契约 |
| [rule-orchestrator-scope.md](./rules/rule-orchestrator-scope.md) | Orchestrator 边界 |
| [rule-mock-toggle.md](./rules/rule-mock-toggle.md) | Mock 切换规则 |

---

## 交付物

| 文档 | 说明 |
|------|------|
| [DEMO_SCRIPT.md](./DEMO_SCRIPT.md) | 3 分钟 Demo 视频剧本 |
| [ACCEPTANCE_CHECKLIST.md](./ACCEPTANCE_CHECKLIST.md) | 功能验收清单 |

---

## 推荐开发顺序

1. 数据模型：User / Agent / Conversation / Message / Artifact
2. 会话列表和新建会话
3. 单 Agent 消息发送闭环
4. SSE 消息推送
5. Provider Adapter 接入
6. Artifact 卡片渲染
7. 群聊和 @Agent
8. Orchestrator 调度
9. 用户自建 Agent
10. 部署状态卡片和 Demo 优化

---

## 快速索引

### 开发新功能

1. 查阅 `SPEC_API.md` 确认接口设计
2. 使用 `skill-java-api-dev.md` 或 `skill-frontend-chat-ui.md`
3. 遵循 `rule-naming-convention.md` 和 `rule-file-organization.md`

### 接入新 Provider

1. 阅读 `SPEC_AGENT_ADAPTER.md`
2. 使用 `skill-agent-provider-onboard.md`
3. 遵循 `rule-agent-adapter-contract.md` 和 `rule-mock-toggle.md`

### 修改数据模型

1. 阅读 `rule-db-migration.md`
2. 使用 `skill-data-model-change.md`
3. 更新 `SPEC_MESSAGE_FLOW.md`（如涉及消息流）

### Bug 排查

1. 使用 `skill-bug-investigation.md`
2. 参考 `SPEC_MESSAGE_FLOW.md` 追踪数据流

---

## 文档更新记录

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2024-01-15 | v0.1 | 初始版本 |
| 2026-06-02 | v0.2 | 新增 P0 核心文档：架构、数据模型、需求覆盖、消息流Skill、Orchestrator Skill |
| 2026-06-02 | v0.3 | 新增 P1/P2 文档：产品需求、上下文管理、自建Agent、会话管理、Demo脚本、验收清单 |
