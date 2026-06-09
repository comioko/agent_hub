# Skill: 聊天界面开发

## 适用场景

开发或修改聊天相关 UI 组件，包括消息列表、输入框、会话列表等。

## 输入

- 设计稿或功能描述
- 相关的 API 接口
- 现有组件结构

## 输出

完整的 React 组件，可直接运行。

## 执行步骤

### 1. 分析组件层级

```
ChatPage (主页面)
├── Header (头部)
├── SessionList (左侧会话列表)
│   └── SessionItem (单个会话项)
└── ChatArea (右侧聊天区域)
    ├── MessageList (消息列表)
    │   └── MessageItem (单条消息)
    │       └── CodeBlock (代码块)
    └── MessageInput (输入框)
```

### 2. 使用 Zustand Store

已有的 Store：
- `authStore`：用户认证状态
- `sessionStore`：会话列表、当前会话
- `messageStore`：消息列表

```javascript
// 在组件中使用
import { useSessionStore } from '../stores/sessionStore'
import { useMessageStore } from '../stores/messageStore'

function ChatPage() {
  const { sessions, currentSession, fetchSessions } = useSessionStore()
  const { messages, fetchMessages } = useMessageStore()

  // 组件逻辑
}
```

### 3. 实现组件

#### SessionList

```jsx
export default function SessionList({ sessions, currentSession, onSelect }) {
  return (
    <div className="h-full bg-gray-800 flex flex-col">
      <div className="p-4 border-b border-gray-700">
        <button onClick={onNewSession} className="w-full btn-primary">
          New Chat
        </button>
      </div>

      <div className="flex-1 overflow-y-auto">
        {sessions.map(session => (
          <SessionItem
            key={session.id}
            session={session}
            active={currentSession?.id === session.id}
            onClick={() => onSelect(session)}
          />
        ))}
      </div>
    </div>
  )
}
```

#### MessageItem

```jsx
export default function MessageItem({ message }) {
  const isUser = message.senderType === 'USER'

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div className={`max-w-[70%] ${isUser ? 'order-2' : 'order-1'}`}>
        <div className="flex items-center gap-2 mb-1">
          {!isUser && (
            <span className="text-sm font-medium text-primary-400">
              {message.senderName}
            </span>
          )}
          <span className="text-xs text-gray-500">
            {formatTime(message.createdAt)}
          </span>
        </div>

        <div className={`rounded-lg px-4 py-3 ${isUser ? 'bg-primary-600' : 'bg-gray-800'}`}>
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {message.content}
          </ReactMarkdown>

          {/* 渲染 Artifact blocks */}
          {message.blocks?.map(block => (
            <ArtifactRenderer key={block.id} block={block} />
          ))}
        </div>
      </div>
    </div>
  )
}
```

#### MessageInput

```jsx
export default function MessageInput({ onSend }) {
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!content.trim() || loading) return

    setLoading(true)
    try {
      await onSend(content.trim())
      setContent('')
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex items-center gap-3">
      <input
        type="text"
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="Type your message..."
        className="flex-1 input"
        disabled={loading}
      />
      <button type="submit" disabled={!content.trim() || loading} className="btn-primary">
        {loading ? 'Sending...' : 'Send'}
      </button>
    </form>
  )
}
```

### 4. 集成 API

使用 `src/api/agenthub.js` 中的封装：

```javascript
import { messageApi, sessionApi } from '../api/agenthub'

// 发送消息
const handleSend = async (content) => {
  const response = await messageApi.sendMessage(conversationId, content)
  // 处理响应
}

// 获取消息
const loadMessages = async (conversationId) => {
  const response = await messageApi.getMessages(conversationId)
  messageStore.setMessages(response.data)
}
```

### 5. 集成 SSE

使用 `useSSE` hook：

```javascript
import { useSSE } from '../hooks/useSSE'

function ChatArea({ conversationId }) {
  const { addMessage } = useMessageStore()

  const handleSSEMessage = (data) => {
    if (data.conversationId === conversationId) {
      addMessage(data)
    }
  }

  const { connect, disconnect } = useSSE(handleSSEMessage)

  useEffect(() => {
    if (conversationId) {
      connect()
    }
    return () => disconnect()
  }, [conversationId, connect, disconnect])
}
```

## 样式规范

使用 Tailwind CSS：

```jsx
// 容器
<div className="h-screen flex bg-gray-900">

// 侧边栏
<div className="w-64 flex-shrink-0 bg-gray-800">

// 消息气泡
<div className="bg-primary-600 text-white rounded-lg px-4 py-3">

// 输入框
<input className="px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white">

// 按钮
<button className="px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded">
```

## 注意事项

1. **XSS 防护**：用户输入使用 React 的自动转义，不要使用 `dangerouslySetInnerHTML`
2. **SSE 重连**：使用 `useSSE` hook 处理重连逻辑
3. **消息列表优化**：考虑使用虚拟滚动（react-virtual）优化大量消息
4. **加载状态**：所有异步操作需要 loading 状态
5. **错误处理**：API 错误需要 toast 或 inline 提示

## 常用工具函数

```javascript
// 时间格式化
const formatTime = (dateStr) => {
  const date = new Date(dateStr)
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

// 日期格式化
const formatDate = (dateStr) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  if (days < 7) return `${days} days ago`
  return date.toLocaleDateString()
}
```

## 检查清单

- [ ] 组件正确使用 Zustand Store
- [ ] API 调用使用封装的函数
- [ ] SSE 使用 useSSE hook
- [ ] 样式使用 Tailwind CSS
- [ ] 加载/错误状态处理
- [ ] 时间格式化显示
- [ ] 响应式布局适配
