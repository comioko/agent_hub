# SPEC_REQUIREMENT_COVERAGE.md

## 概述

本文档将 PDF 需求与项目文档/功能进行映射，明确各需求的优先级、对应文档和当前状态。

---

## 需求覆盖矩阵

| PDF 要求 | 优先级 | 对应文档 | 当前状态 | 备注 |
|---|---|---|---|---|
| 会话列表（最近活跃排序） | P0 | SPEC_API.md, skill-session-management.md | 已设计 | 需补充会话管理Skill |
| 新建会话（选择Agent） | P0 | SPEC_API.md, skill-session-management.md | 已设计 | 需补充会话管理Skill |
| 单聊模式 | P0 | SPEC_MESSAGE_FLOW.md | 已设计 | - |
| 消息发送与历史 | P0 | SPEC_MESSAGE_FLOW.md | 已设计 | - |
| SSE流式更新 | P0 | SPEC_MESSAGE_FLOW.md | 已实现 | - |
| Agent Adapter接入 | P0 | SPEC_AGENT_ADAPTER.md, skill-agent-provider-onboard.md | 已设计 | - |
| 多轮上下文 | P0 | SPEC_MESSAGE_FLOW.md | 已设计 | 需补充上下文管理 |
| 消息类型（文本/代码块） | P0 | SPEC_ARTIFACT.md | 已实现 | - |
| Artifact卡片渲染 | P1 | SPEC_ARTIFACT.md, skill-artifact-extension.md | 已实现 | - |
| 群聊模式 | P1 | SPEC_ORCHESTRATOR.md | 已设计 | 需完善Orchestrator Skill |
| @Agent提及 | P1 | skill-mention-agent.md | 缺失 | 需新增Skill |
| Orchestrator多Agent调度 | P1 | SPEC_ORCHESTRATOR.md, skill-orchestrator-dev.md | 已设计 | 需补充Orchestrator Skill |
| 用户自建Agent | P1 | skill-custom-agent-create.md | 缺失 | 需新增Skill |
| 上下文连续（历史消息） | P0 | SPEC_CONTEXT.md, skill-context-management.md | 缺失 | 需新增文档 |
| Pin关键消息 | P1 | SPEC_CONTEXT.md | 缺失 | 需新增文档 |
| 代码Diff卡片 | P1 | SPEC_ARTIFACT.md | 已设计 | - |
| 网页预览卡片 | P1 | SPEC_ARTIFACT.md | 已设计 | - |
| 文件附件卡片 | P1 | SPEC_ARTIFACT.md | 已设计 | - |
| 部署状态卡片 | P2 | SPEC_DEPLOYMENT.md | 缺失 | P2阶段 |
| Diff应用 | P2 | SPEC_VERSIONING_DIFF.md | 缺失 | P2阶段 |
| 版本历史 | P2 | SPEC_VERSIONING_DIFF.md | 缺失 | P2阶段 |
| 会话置顶/归档/搜索 | P2 | skill-session-management.md | 缺失 | P2阶段 |
| AI协作记录 | P0 | AI_COLLAB_LOG.md | 缺失 | 需新增交付物 |

---

## 核心链路覆盖情况

### P0 核心链路（已覆盖）

```
用户发送消息 → 后端保存 → Agent调用 → SSE推送 → 前端渲染
```

| 环节 | 对应文档 | 状态 |
|---|---|---|
| 消息发送API | SPEC_API.md | ✅ 已设计 |
| 消息持久化 | SPEC_MESSAGE_FLOW.md | ✅ 已设计 |
| Agent调用 | SPEC_AGENT_ADAPTER.md | ✅ 已设计 |
| SSE推送 | SPEC_MESSAGE_FLOW.md | ✅ 已实现 |
| 前端渲染 | skill-frontend-chat-ui.md | ✅ 已设计 |

### P1 扩展链路（部分覆盖）

```
用户@Agent → 消息解析 → Orchestrator调度 → 多Agent并行/串行 → 结果聚合 → SSE推送
```

| 环节 | 对应文档 | 状态 |
|---|---|---|
| @Agent解析 | skill-mention-agent.md | ❌ 缺失 |
| Orchestrator调度 | SPEC_ORCHESTRATOR.md | ✅ 已设计 |
| 多Agent调用 | skill-orchestrator-dev.md | ❌ 缺失Skill |
| 结果聚合 | SPEC_ORCHESTRATOR.md | ⚠️ 待完善 |

---

## 缺失文档清单

### P0 必补

- [ ] `SPEC_CONTEXT.md` - 上下文管理规范
- [ ] `skill-context-management.md` - 上下文开发Skill
- [ ] `skill-orchestrator-dev.md` - Orchestrator调度开发Skill
- [ ] `skill-message-flow-dev.md` - 消息流开发Skill

### P1 建议补

- [ ] `skill-custom-agent-create.md` - 用户自建Agent开发Skill
- [ ] `skill-mention-agent.md` - @Agent开发Skill
- [ ] `skill-session-management.md` - 会话管理开发Skill
- [ ] `SPEC_PRODUCT_REQUIREMENTS.md` - 产品需求总纲

### P2 占位

- [ ] `SPEC_DEPLOYMENT.md` - 部署发布规范
- [ ] `SPEC_VERSIONING_DIFF.md` - Diff与版本规范
- [ ] `DEMO_SCRIPT.md` - Demo视频脚本
- [ ] `ACCEPTANCE_CHECKLIST.md` - 功能验收清单

---

## 验收标准

### P0 功能验收

- [ ] 用户可以创建新会话并选择Agent
- [ ] 用户发送消息后，Agent能立即响应
- [ ] 消息历史能正确加载和展示
- [ ] SSE流式更新正常工作
- [ ] 代码块能正确渲染

### P1 功能验收

- [ ] 群聊中@Agent能被正确解析
- [ ] 多Agent能依次响应用户消息
- [ ] Artifact卡片（代码/Diff/预览）能正确展示
- [ ] 用户能创建自定义Agent

### P2 功能验收

- [ ] 部署状态卡片能展示构建进度
- [ ] Diff能一键应用到代码库
- [ ] 会话支持置顶/归档/搜索
