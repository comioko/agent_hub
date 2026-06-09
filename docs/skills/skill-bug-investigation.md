# Skill: Bug 排查

## 适用场景

线上/线下问题定位，包括功能异常、性能问题、崩溃等。

## 输入

- 错误现象描述
- 错误日志/截图
- 复现步骤（如有）

## 输出

问题根因 + 修复方案。

## 执行步骤

### 1. 复现问题

```bash
# 确认触发条件
- 什么操作触发了问题？
- 是否必现？
- 影响范围多大？
```

### 2. 收集信息

**前端**：
- 浏览器 Console 错误
- Network 请求/响应
- 页面截图

**后端**：
```bash
# 查看日志
tail -f backend/logs/spring.log

# 查看错误堆栈
grep -A 20 "Exception" backend/logs/spring.log
```

**数据库**：
```sql
-- 查看慢查询
SHOW PROCESSLIST;

-- 查看锁等待
SELECT * FROM information_schema.INNODB_LOCK_WAITS;
```

### 3. 定位问题

**链路追踪法**：

```
用户操作
    │
    ▼
前端请求 ──────────────────────────────►
    │                                  │
    ▼                                  ▼
后端 Controller ────► Service ────► Repository
    │                   │                │
    ▼                   ▼                ▼
返回响应           业务逻辑          SQL 执行
```

**常见问题定位**：

| 问题 | 排查点 |
|------|--------|
| 接口 500 | 后端日志、异常堆栈 |
| 接口 400 | 请求参数校验 |
| 前端白屏 | 浏览器 Console、Network |
| SSE 断开 | 网络、WebSocket 状态 |
| Agent 无响应 | Adapter 实现、超时设置 |
| 消息丢失 | 数据库写入、SSE 推送 |

### 4. 分析根因

记录：
- 问题发生的直接原因
- 问题发生的根本原因
- 为什么之前没有发现

### 5. 修复验证

```bash
# 单元测试
mvn test -Dtest=ClassName#methodName

# 集成测试
mvn verify

# 前端验证
npm run dev
```

## 常见问题处理

### 1. JWT Token 过期

```
现象：接口返回 401
排查：检查 token 是否过期、是否正确传递
修复：前端刷新 token 或重新登录
```

### 2. 数据库连接池耗尽

```
现象：接口响应慢或超时
排查：SHOW PROCESSLIST 发现大量连接
修复：增加最大连接数或优化慢查询
```

### 3. SSE 连接断开

```
现象：消息推送突然停止
排查：
  - 检查后端 SseEmitter 状态
  - 检查网络连接
  - 检查后端是否重启
修复：
  - 前端重连机制
  - 后端保活机制
```

### 4. Agent 调用超时

```
现象：消息发送后长时间无响应
排查：
  - 检查 Adapter 超时设置
  - 检查 Provider API 状态
修复：
  - 调整超时时间
  - 添加重试机制
```

### 5. CORS 问题

```
现象：浏览器报 CORS 错误
排查：检查后端 CORS 配置
修复：确保允许前端域名
```

## 日志规范

后端应输出结构化日志：

```java
// 记录请求
log.info("Received message: conversationId={}, userId={}", conversationId, userId);

// 记录错误
log.error("Agent call failed: conversationId={}, error={}",
    conversationId, e.getMessage(), e);

// 记录关键操作
log.info("Message sent: messageId={}, agentId={}", messageId, agentId);
```

## 问题报告模板

```markdown
## 问题描述
[简要描述问题]

## 复现步骤
1. [步骤1]
2. [步骤2]
3. [步骤3]

## 影响范围
- 用户：[影响范围]
- 功能：[影响功能]

## 根因分析
[分析结果]

## 修复方案
[修复方案描述]

## 验证结果
[测试结果]
```

## 常用命令

```bash
# 后端日志
tail -f backend/logs/spring.log | grep "ERROR"

# MySQL 慢查询
mysql -u root -p -e "SHOW VARIABLES LIKE 'slow_query_log%';"

# 查看端口占用
lsof -i :8080

# 查看进程
ps aux | grep java
```

## 检查清单

- [ ] 问题可复现
- [ ] 已收集完整日志
- [ ] 根因已确定
- [ ] 修复方案已验证
- [ ] 修复后测试通过
- [ ] 问题报告已记录
