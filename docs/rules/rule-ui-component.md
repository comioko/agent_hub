# Rule: UI 组件规范

## 目的

保证前端组件质量和一致性。

## 组件设计原则

### 单一职责

一个组件只负责一个功能：

```
✓ MessageItem       - 渲染单条消息
✓ CodeBlock         - 渲染代码块
✓ MessageInput      - 消息输入框

✗ MessageListWithInput  - 合并了两个职责
```

### 组件拆分时机

| 条件 | 建议 |
|------|------|
| 超过 200 行 | 考虑拆分 |
| 多个关注点 | 拆分为子组件 |
| 可复用 | 提取为通用组件 |

## Props 规范

### 类型检查

```jsx
import PropTypes from 'prop-types'

MessageItem.propTypes = {
  message: PropTypes.shape({
    id: PropTypes.number.isRequired,
    content: PropTypes.string.isRequired,
    senderType: PropTypes.oneOf(['USER', 'AGENT']).isRequired,
    createdAt: PropTypes.string.isRequired
  }).isRequired,
  onReply: PropTypes.func
}
```

### 默认值

```jsx
MessageItem.defaultProps = {
  onReply: () => {}
}
```

### 常用 PropTypes

```jsx
PropTypes.string
PropTypes.number
PropTypes.bool
PropTypes.array
PropTypes.object
PropTypes.func
PropTypes.oneOf(['value1', 'value2'])
PropTypes.shape({ ... })
PropTypes.arrayOf(PropTypes.shape({ ... }))
```

## 样式规范

### Tailwind CSS

```jsx
// 布局
<div className="flex items-center justify-between">

// 颜色
<button className="bg-primary-600 hover:bg-primary-700 text-white">

// 间距
<div className="p-4 m-2 space-y-4">

// 响应式
<div className="w-full md:w-64">
```

### 禁止

- 禁止内联样式（除非动态值）
- 禁止使用未定义的 class
- 禁止重复样式

## 事件处理

```jsx
// 使用箭头函数或 bind
<button onClick={() => handleClick(id)}>

// 传递事件对象
<form onSubmit={(e) => handleSubmit(e)}>

// 正确移除事件监听
useEffect(() => {
  return () => {
    window.removeEventListener('resize', handleResize)
  }
}, [handleResize])
```

## 状态管理

### 本地状态 vs Store

```jsx
// 本地状态：组件私有
function MessageInput() {
  const [content, setContent] = useState('')
  // ...
}

// Store 状态：跨组件共享
function ChatPage() {
  const { messages } = useMessageStore()
  // ...
}
```

## 无障碍

### 基本要求

```jsx
// 图片必须有 alt
<img src="avatar.png" alt="User avatar" />

// 按钮必须有文字或 aria-label
<button aria-label="Close dialog">
  <Icon />
</button>

// 表单必须有 label
<label htmlFor="username">Username</label>
<input id="username" />

// 使用语义化标签
<button> 而不是 <div onClick>
<nav> 而不是 <div className="nav"
```

## 组件文件结构

```jsx
// components/MessageItem.jsx

import PropTypes from 'prop-types'
import { formatTime } from '../utils/date'

// 组件实现
export default function MessageItem({ message, onReply }) {
  // ...
}

// PropTypes
MessageItem.propTypes = { ... }

// 默认值
MessageItem.defaultProps = { ... }

// 辅助函数（可单独文件）
function formatTime(dateStr) { ... }
```

## 常用组件模板

### Button

```jsx
export default function Button({ children, variant = 'primary', size = 'md', disabled, onClick }) {
  const variants = {
    primary: 'bg-primary-600 hover:bg-primary-700 text-white',
    secondary: 'bg-gray-200 hover:bg-gray-300 text-gray-800',
    danger: 'bg-red-600 hover:bg-red-700 text-white'
  }

  const sizes = {
    sm: 'px-3 py-1 text-sm',
    md: 'px-4 py-2',
    lg: 'px-6 py-3 text-lg'
  }

  return (
    <button
      className={`${variants[variant]} ${sizes[size]} rounded font-medium disabled:opacity-50`}
      disabled={disabled}
      onClick={onClick}
    >
      {children}
    </button>
  )
}
```

## 检查清单

- [ ] PropTypes 定义完整
- [ ] 样式使用 Tailwind CSS
- [ ] 有 loading 状态
- [ ] 有错误处理
- [ ] 响应式适配
- [ ] 无障碍基本满足
