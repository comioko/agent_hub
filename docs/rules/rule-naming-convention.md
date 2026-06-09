# Rule: 命名规范

## 目的

统一命名风格，提高代码可读性和可维护性。

## Java 后端命名

### 类名

| 类型 | 规范 | 示例 |
|------|------|------|
| 实体类 | UpperCamelCase | `User`, `Message`, `Conversation` |
| Service | UpperCamelCase + Service | `UserService`, `MessageService` |
| Controller | UpperCamelCase + Controller | `UserController`, `MessageController` |
| Mapper | UpperCamelCase + Mapper | `UserMapper`, `MessageMapper` |
| DTO/VO | UpperCamelCase + Request/Response/VO | `LoginRequest`, `UserVO` |
| 枚举 | UpperCamelCase | `SenderType`, `MessageType` |
| Adapter | UpperCamelCase + Adapter | `ClaudeAdapter`, `OpenAIAdapter` |
| 配置类 | UpperCamelCase + Config | `SecurityConfig`, `JwtConfig` |
| 异常类 | UpperCamelCase + Exception | `BusinessException`, `AgentException` |

### 方法名

| 类型 | 规范 | 示例 |
|------|------|------|
| 获取 | get + 名词 | `getUser()`, `getUserById()` |
| 查询 | find + 名词 | `findByUsername()` |
| 保存 | save / create | `saveUser()`, `createSession()` |
| 更新 | update | `updateMessage()` |
| 删除 | delete / remove | `deleteSession()`, `removeMessage()` |
| 布尔 | is / has / can + 形容词 | `isAvailable()`, `hasPermission()` |
| 列表 | list / getAll | `listConversations()`, `getAllAgents()` |

### 常量

| 类型 | 规范 | 示例 |
|------|------|------|
| 枚举值 | UPPER_SNAKE_CASE | `SINGLE`, `MAX_RETRY_COUNT` |
| 静态常量 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE = 100` |

### 包名

```
com.agenthub
├── config/           # 配置类
├── controller/       # REST 控制器
├── service/         # 业务逻辑
├── agent/          # Agent 核心
├── adapter/        # Provider 适配器
├── repository/     # 数据访问
├── model/
│   ├── entity/    # 数据库实体
│   ├── dto/       # 数据传输对象
│   └── enums/     # 枚举类
└── exception/      # 异常处理
```

## 数据库命名

| 类型 | 规范 | 示例 |
|------|------|------|
| 表名 | snake_case, 复数 | `users`, `messages`, `conversation_participants` |
| 主键 | `id` | `id` |
| 外键 | `{ref_table}_id` | `user_id`, `conversation_id` |
| 索引 | `idx_{table}_{column}` | `idx_message_conv_time` |
| 字段 | snake_case | `created_at`, `updated_at` |

### 字段命名对照

| Java | Database |
|------|----------|
| createdAt | `created_at` |
| updatedAt | `updated_at` |
| userId | `user_id` |
| isEnabled | `enabled` |
| passwordHash | `password_hash` |

## 前端命名

### 文件名

| 类型 | 规范 | 示例 |
|------|------|------|
| 组件 | PascalCase.jsx | `MessageList.jsx`, `ChatPage.jsx` |
| Store | camelCase + Store.js | `authStore.js`, `sessionStore.js` |
| Hook | camelCase + use.js | `useSSE.js`, `useAuth.js` |
| API | snake_case.js | `agenthub.js` |
| 样式 | index.css | `index.css`, `chat.css` |

### React 组件

```jsx
// 组件名：PascalCase
export default function MessageList() { }

// Hook：camelCase 以 use 开头
export function useSSE() { }
```

### CSS 类名

使用 Tailwind CSS 的语义化类名：

```jsx
// 布局
<div className="flex items-center justify-between">

// 颜色
<button className="bg-primary-600 text-white">

// 间距
<div className="p-4 m-2">

// 响应式
<div className="w-full md:w-64">
```

### 变量命名

```javascript
// 组件内：camelCase
const [loading, setLoading] = useState(false)
const messageList = []

// Store state：camelCase
const { user, token, login } = useAuthStore()

// 常量：UPPER_SNAKE_CASE
const MAX_RETRY_COUNT = 3
```

## API 命名

### 路径

```
/api/{资源}/{操作}
/api/sessions          # 会话列表
/api/sessions/{id}      # 会话详情
/api/messages          # 消息列表
```

### 请求参数

```json
{
  "conversationId": 1,    // camelCase
  "messageType": "TEXT"   // camelCase
}
```

### 响应字段

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "userId": 1,
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

## 禁止

- 禁止使用中文命名
- 禁止使用缩写（除非是公认的）
- 禁止混用命名风格
- 禁止使用无意义名称如 `temp`, `tmp`, `data1`
