# AgentHub API 规范

## 响应格式

所有 API 响应必须使用统一 JSON 格式：

```json
{
  "code": 0,
  "message": "OK",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 0=成功，非0=失败 |
| message | string | 状态描述 |
| data | object | 业务数据，可为 null |

## 错误码

| 区间 | 类别 | 示例 |
|------|------|------|
| 0 | 成功 | - |
| 1001-1999 | 参数错误 | 1001: 参数校验失败 |
| 2001-2999 | 业务错误 | 2001: 会话不存在, 2002: 无访问权限 |
| 3001-3999 | Agent 错误 | 3001: Agent 调用失败, 3002: Agent 不可用 |
| 5000+ | 系统错误 | 5001: 数据库错误 |

## 认证

使用 JWT Bearer Token：

```
Authorization: Bearer <token>
```

## API 端点

### 认证

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /api/auth/register | 注册 | 否 |
| POST | /api/auth/login | 登录 | 否 |
| GET | /api/auth/me | 获取当前用户 | 是 |

### 会话

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | /api/sessions | 列表 | 是 |
| POST | /api/sessions | 创建 | 是 |
| GET | /api/sessions/:id | 详情 | 是 |
| DELETE | /api/sessions/:id | 删除 | 是 |

### 消息

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | /api/messages/conversation/:id | 消息列表 | 是 |
| POST | /api/messages | 发送消息 | 是 |
| GET | /api/messages/subscribe | SSE 订阅 | 是 |

### Agent

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | /api/agents | 列表 | 否 |
| GET | /api/agents/:id | 详情 | 否 |

## 请求/响应示例

### POST /api/auth/login

Request:
```json
{
  "username": "user1",
  "password": "password123"
}
```

Response:
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 1,
      "username": "user1",
      "nickname": "User One",
      "avatarUrl": null
    }
  }
}
```

### POST /api/sessions

Request:
```json
{
  "agentId": 1,
  "title": "Chat with Assistant"
}
```

Response:
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": 1,
    "title": "Chat with Assistant",
    "type": "SINGLE",
    "ownerId": 1,
    "createdAt": "2024-01-15T10:00:00",
    "updatedAt": "2024-01-15T10:00:00",
    "participants": [
      {
        "id": 1,
        "userId": 1,
        "agentId": null,
        "name": "user1",
        "avatarUrl": null,
        "role": "OWNER",
        "type": "USER"
      },
      {
        "id": 2,
        "userId": null,
        "agentId": 1,
        "name": "Assistant",
        "avatarUrl": null,
        "role": "MEMBER",
        "type": "AGENT"
      }
    ]
  }
}
```

### POST /api/messages

Request:
```json
{
  "conversationId": 1,
  "content": "Hello, help me write a function",
  "parentId": null
}
```

Response:
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": 10,
    "conversationId": 1,
    "senderType": "USER",
    "senderId": 1,
    "senderName": "You",
    "senderAvatar": null,
    "content": "Hello, help me write a function",
    "messageType": "TEXT",
    "parentId": null,
    "createdAt": "2024-01-15T10:30:00",
    "blocks": []
  }
}
```

### GET /api/messages/subscribe (SSE)

SSE Event: `message`
```json
{
  "id": 11,
  "conversationId": 1,
  "senderType": "AGENT",
  "senderId": 1,
  "senderName": "Assistant",
  "senderAvatar": null,
  "content": "Sure! Here's a Python function...",
  "messageType": "TEXT",
  "parentId": 10,
  "createdAt": "2024-01-15T10:30:05",
  "blocks": [
    {
      "id": 1,
      "blockType": "CODE",
      "content": "def hello():\n    pass",
      "language": "python",
      "metadata": "{\"filename\": \"hello.py\"}"
    }
  ]
}
```

SSE Event: `connected`
```json
"Connected"
```

## 分页

对于列表接口，支持分页参数：

```
GET /api/messages/conversation/1?page=1&size=20
```

Response:
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "list": [],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

## 状态码约定

| HTTP Status | 含义 |
|--------------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |
