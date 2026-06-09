# Skill: 新 Artifact 卡片开发

## 适用场景

新增消息产物卡片类型，如 Diff 卡片、网页预览卡片、部署状态卡片等。

## 输入

- 卡片类型设计
- metadata JSON 格式
- 渲染需求

## 输出

后端存储逻辑 + 前端渲染组件。

## 执行步骤

### 1. 定义枚举值

```java
// model/enums/BlockType.java
public enum BlockType {
    CODE,
    DIFF,
    WEB_PREVIEW,
    FILE,
    DEPLOY_STATUS,
    // 新增
    CHART,
    TABLE
}
```

### 2. 设计 metadata 格式

在 `SPEC_ARTIFACT.md` 中定义 JSON 结构：

```json
// CHART 类型 metadata 示例
{
  "chartType": "bar",           // bar, line, pie, scatter
  "title": "Sales Report",
  "data": {
    "labels": ["Jan", "Feb", "Mar"],
    "datasets": [
      {"label": "Sales", "data": [100, 150, 120]}
    ]
  },
  "options": {
    "responsive": true,
    "maintainAspectRatio": false
  }
}
```

### 3. 后端存储

在 AgentResponse 中添加 block：

```java
AgentResponse response = new AgentResponse();
response.setContent("Here's the chart you requested:");

ArtifactBlock chartBlock = new ArtifactBlock();
chartBlock.setType("CHART");
chartBlock.setContent(null);  // CHART 类型不需要 content

Map<String, Object> metadata = new HashMap<>();
metadata.put("chartType", "bar");
metadata.put("title", "Sales Report");
// ... 添加其他 metadata

chartBlock.setMetadata(new ObjectMapper().writeValueAsString(metadata));
response.getBlocks().add(chartBlock);
```

### 4. 前端渲染组件

创建 `components/ArtifactCards/ChartCard.jsx`：

```jsx
import { useEffect, useRef } from 'react'

export default function ChartCard({ block }) {
  const canvasRef = useRef(null)
  const metadata = parseMetadata(block.metadata)

  useEffect(() => {
    if (!canvasRef.current || !metadata.data) return

    // 使用 Chart.js 或其他库绘制
    const ctx = canvasRef.current.getContext('2d')
    // ... 绑定数据和选项

  }, [metadata])

  return (
    <div className="bg-white rounded-lg p-4 my-2">
      {metadata.title && (
        <h4 className="text-lg font-medium mb-2">{metadata.title}</h4>
      )}
      <div className="h-64">
        <canvas ref={canvasRef} />
      </div>
    </div>
  )
}

function parseMetadata(jsonStr) {
  try {
    return JSON.parse(jsonStr)
  } catch {
    return {}
  }
}
```

### 5. 注册到渲染器

在 `MessageItem.jsx` 中：

```jsx
import ChartCard from './ArtifactCards/ChartCard'
import DiffCard from './ArtifactCards/DiffCard'

function ArtifactRenderer({ block }) {
  switch (block.blockType) {
    case 'CODE':
      return <CodeBlock block={block} />
    case 'DIFF':
      return <DiffCard block={block} />
    case 'CHART':
      return <ChartCard block={block} />
    default:
      return <GenericBlock content={block.content} />
  }
}
```

### 6. 更新文档

在 `SPEC_ARTIFACT.md` 中添加新类型说明。

## 常用卡片实现参考

### CodeBlock（已有）

```jsx
export default function CodeBlock({ block }) {
  const [copied, setCopied] = useState(false)
  const metadata = parseMetadata(block.metadata)

  return (
    <div className="relative group rounded-lg overflow-hidden bg-gray-900">
      <div className="flex justify-between px-4 py-2 bg-gray-800">
        <span className="text-xs text-gray-400 uppercase">
          {block.language || 'code'}
        </span>
        <button onClick={() => copyToClipboard(block.content)}>
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>
      <pre className="p-4 overflow-x-auto">
        <code>{block.content}</code>
      </pre>
    </div>
  )
}
```

### DiffCard

```jsx
export default function DiffCard({ block }) {
  const metadata = parseMetadata(block.metadata)

  return (
    <div className="rounded-lg overflow-hidden bg-gray-900">
      <div className="px-4 py-2 bg-gray-800 text-sm">
        <span className="text-red-400">-{metadata.stats?.deletions || 0}</span>
        <span className="mx-2">/</span>
        <span className="text-green-400">+{metadata.stats?.additions || 0}</span>
      </div>
      <pre className="p-4 text-sm font-mono">
        {block.content.split('\n').map((line, i) => (
          <DiffLine key={i} line={line} />
        ))}
      </pre>
    </div>
  )
}

function DiffLine({ line }) {
  if (line.startsWith('+')) {
    return <div className="text-green-400">{line}</div>
  }
  if (line.startsWith('-')) {
    return <div className="text-red-400">{line}</div>
  }
  return <div className="text-gray-400">{line}</div>
}
```

### DeployStatusCard

```jsx
export default function DeployStatusCard({ block }) {
  const metadata = parseMetadata(block.metadata)

  const statusColors = {
    DEPLOYED: 'bg-green-500',
    FAILED: 'bg-red-500',
    BUILDING: 'bg-yellow-500',
    PENDING: 'bg-gray-500'
  }

  return (
    <div className="bg-gray-800 rounded-lg p-4 my-2">
      <div className="flex items-center gap-3 mb-3">
        <div className={`w-3 h-3 rounded-full ${statusColors[metadata.status]}`} />
        <span className="font-medium">{metadata.status}</span>
        {metadata.deployUrl && (
          <a href={metadata.deployUrl} target="_blank" rel="noopener noreferrer" className="text-primary-400 text-sm">
            Open →
          </a>
        )}
      </div>
      {metadata.environment && (
        <div className="text-sm text-gray-400">Environment: {metadata.environment}</div>
      )}
      {metadata.buildLogs && (
        <details className="mt-2">
          <summary className="text-sm cursor-pointer text-gray-400">Build Logs</summary>
          <pre className="mt-2 p-2 bg-gray-900 rounded text-xs overflow-auto max-h-40">
            {metadata.buildLogs}
          </pre>
        </details>
      )}
    </div>
  )
}
```

## 注意事项

1. **metadata 解析**：始终使用 try-catch 处理 JSON.parse
2. **内容转义**：防止 XSS，特别是 user-generated content
3. **加载状态**：图表等复杂组件需要 loading 状态
4. **响应式**：卡片宽度需要适配移动端
5. **可访问性**：添加适当的 aria-label

## 检查清单

- [ ] BlockType 枚举已添加
- [ ] metadata 格式已定义
- [ ] SPEC_ARTIFACT.md 已更新
- [ ] 后端存储逻辑正确
- [ ] 前端渲染组件实现
- [ ] 组件已注册到 ArtifactRenderer
- [ ] 有适当的样式和状态处理
