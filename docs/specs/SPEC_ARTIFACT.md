# Artifact 卡片规范

## 作用

定义消息中产物卡片的扩展机制，支持代码、Diff、网页预览、文件等多种富媒体展示。

## block_type 枚举

| type | 说明 | 必需字段 | 可选字段 |
|------|------|----------|----------|
| CODE | 代码块 | content, language | filename, highlight |
| DIFF | 代码变更 | content | language, hunks |
| WEB_PREVIEW | 网页预览 | url, title | screenshot, description |
| FILE | 文件附件 | filename, content | size, type |
| DEPLOY_STATUS | 部署状态 | status, url | logs, timestamp |

## 数据库存储

### message_block 表

```sql
CREATE TABLE IF NOT EXISTS message_block (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    block_type VARCHAR(30) NOT NULL,
    content TEXT,                    -- 代码内容、diff 等
    language VARCHAR(50),           -- 编程语言
    metadata TEXT,                  -- JSON 扩展信息
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_message (message_id),
    FOREIGN KEY (message_id) REFERENCES message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## metadata 格式

### CODE

```json
{
  "filename": "hello.py",
  "highlight": "1-5,10",
  "annotations": ["security", "performance"]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| filename | string | 文件名 |
| highlight | string | 高亮行号，如 "1-5,10" |
| annotations | string[] | 标注列表 |

### DIFF

```json
{
  "language": "python",
  "hunks": [
    {
      "header": "@@ -1,5 +1,6 @@",
      "lines": [
        {"type": "context", "content": " def hello():", "oldLine": 1, "newLine": 1},
        {"type": "add", "content": "+    pass", "newLine": 2},
        {"type": "delete", "content": "-    return None", "oldLine": 2}
      ]
    }
  ],
  "stats": {
    "additions": 10,
    "deletions": 3,
    "files": 2
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| language | string | 编程语言 |
| hunks | object[] | 变更块 |
| stats | object | 统计信息 |

### WEB_PREVIEW

```json
{
  "url": "https://example.com",
  "title": "Example Site",
  "description": "A sample website",
  "favicon": "https://example.com/favicon.ico",
  "screenshot": "https://example.com/screenshot.jpg"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| url | string | 目标 URL |
| title | string | 页面标题 |
| description | string | 页面描述 |
| favicon | string | Favicon URL |
| screenshot | string | 截图 URL（可选） |

### DEPLOY_STATUS

```json
{
  "status": "DEPLOYED",
  "deployUrl": "https://app.example.com",
  "buildLogs": "Build completed in 30s\nDeploy successful",
  "timestamp": "2024-01-15T10:30:00Z",
  "environment": "production",
  "commit": "abc123"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| status | string | DEPLOYED, FAILED, BUILDING, PENDING |
| deployUrl | string | 部署后访问 URL |
| buildLogs | string | 构建日志 |
| timestamp | string | 部署时间 |
| environment | string | 环境名称 |
| commit | string | Git commit hash |

## 前端渲染规范

### 组件结构

```
MessageItem
├── Content (markdown)
└── Blocks[]
    ├── CodeBlock
    ├── DiffCard
    ├── WebPreviewCard
    ├── FileCard
    └── DeployStatusCard
```

### 渲染器实现

```jsx
import CodeBlock from './CodeBlock'
import DiffCard from './DiffCard'
import WebPreviewCard from './WebPreviewCard'
import DeployStatusCard from './DeployStatusCard'

function ArtifactRenderer({ block }) {
  switch (block.blockType) {
    case 'CODE':
      return <CodeBlock block={block} />

    case 'DIFF':
      return <DiffCard block={block} />

    case 'WEB_PREVIEW':
      return <WebPreviewCard metadata={parseJSON(block.metadata)} />

    case 'DEPLOY_STATUS':
      return <DeployStatusCard metadata={parseJSON(block.metadata)} />

    default:
      return <GenericBlock content={block.content} />
  }
}

function parseJSON(jsonStr) {
  try {
    return JSON.parse(jsonStr)
  } catch {
    return {}
  }
}
```

### CodeBlock 组件

```jsx
export default function CodeBlock({ block }) {
  const metadata = parseJSON(block.metadata)
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    await navigator.clipboard.writeText(block.content)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="relative group rounded-lg overflow-hidden bg-gray-900 my-2">
      <div className="flex items-center justify-between px-4 py-2 bg-gray-800 border-b border-gray-700">
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-400 uppercase">
            {block.language || 'code'}
          </span>
          {metadata.filename && (
            <span className="text-xs text-gray-500">{metadata.filename}</span>
          )}
        </div>
        <button
          onClick={handleCopy}
          className="text-gray-400 hover:text-white text-xs"
        >
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>
      <pre className="p-4 overflow-x-auto">
        <code className={`language-${block.language} text-sm text-gray-100`}>
          {block.content}
        </code>
      </pre>
    </div>
  )
}
```

## Agent 返回 Artifact

### Java 后端构建

```java
AgentResponse response = new AgentResponse();
response.setContent("Here's the code you requested:");

ArtifactBlock codeBlock = new ArtifactBlock();
codeBlock.setType("CODE");
codeBlock.setContent("def hello():\n    pass");
codeBlock.setLanguage("python");

Map<String, Object> metadata = new HashMap<>();
metadata.put("filename", "hello.py");
metadata.put("highlight", "1-2");
codeBlock.setMetadata(new ObjectMapper().writeValueAsString(metadata));

response.getBlocks().add(codeBlock);
```

## 扩展新卡片类型

### 步骤 1: 添加枚举值

```java
// model/enums/BlockType.java
public enum BlockType {
    CODE,
    DIFF,
    WEB_PREVIEW,
    FILE,
    DEPLOY_STATUS,
    // 新增类型
    CHART,
    TABLE
}
```

### 步骤 2: 定义 metadata 格式

在本规范中添加新的 metadata 格式定义。

### 步骤 3: 前端实现渲染器

```jsx
// components/ArtifactCards/ChartCard.jsx
export default function ChartCard({ block }) {
  const metadata = parseJSON(block.metadata)
  // 渲染逻辑
}
```

### 步骤 4: 注册到渲染器

```jsx
function ArtifactRenderer({ block }) {
  switch (block.blockType) {
    // 现有 case...
    case 'CHART':
      return <ChartCard block={block} />
    // ...
  }
}
```

## 最佳实践

1. **内容长度限制**：单个 block 的 content 建议不超过 100KB
2. **大文件处理**：超过限制的文件使用 FILE 类型，提供下载链接
3. **XSS 防护**：所有 content 渲染前做 HTML 转义
4. **加载状态**：图片类卡片需要处理加载中/失败状态
5. **响应式**：卡片宽度适配移动端
