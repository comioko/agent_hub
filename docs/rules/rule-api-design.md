# Rule: API 设计规则

## 目的

统一 REST API 设计，保证前后端协作效率。

## 基本规范

### RESTful 原则

| 操作 | HTTP 方法 | 说明 |
|------|-----------|------|
| 查询 | GET | 不修改数据 |
| 创建 | POST | 资源创建 |
| 更新 | PUT/PATCH | 资源更新 |
| 删除 | DELETE | 删除资源 |

### 路径规范

```
# 资源命名使用复数
/api/users
/api/sessions
/api/messages

# 嵌套资源
/api/sessions/{sessionId}/messages
/api/messages/{messageId}/blocks

# 操作使用 HTTP 方法
POST   /api/sessions          # 创建会话
GET    /api/sessions          # 列表
GET    /api/sessions/{id}     # 详情
DELETE /api/sessions/{id}     # 删除
```

### 查询参数

```
# 分页
GET /api/messages?conversationId=1&page=1&size=20

# 过滤
GET /api/agents?enabled=true

# 排序
GET /api/sessions?sort=createdAt,desc
```

## 响应格式

### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {}
}
```

### 错误响应

```json
{
  "code": 1001,
  "message": "Validation failed",
  "data": null
}
```

### 列表响应

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

## 请求格式

### JSON 请求体

```json
POST /api/messages
Content-Type: application/json

{
  "conversationId": 1,
  "content": "Hello"
}
```

### 表单数据

```
POST /api/upload
Content-Type: multipart/form-data

file: <binary>
```

## 认证

### Bearer Token

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

## HTTP 状态码

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| 200 | OK | 成功查询、更新 |
| 201 | Created | 成功创建 |
| 204 | No Content | 成功删除 |
| 400 | Bad Request | 参数错误 |
| 401 | Unauthorized | 未认证 |
| 403 | Forbidden | 无权限 |
| 404 | Not Found | 资源不存在 |
| 500 | Internal Error | 服务器错误 |

## 版本管理

如需 API 版本升级：

```
/api/v1/resources
/api/v2/resources
```

## 禁止

- 禁止在 GET 请求中修改数据
- 禁止返回非 JSON 格式（除 SSE/文件下载）
- 禁止返回纯字符串作为响应（必须包装）
- 禁止在路径中使用动词（用 HTTP 方法表达）
