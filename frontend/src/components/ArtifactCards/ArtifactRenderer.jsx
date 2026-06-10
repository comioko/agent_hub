import CodeBlock from '../CodeBlock'
import DiffCard from './DiffCard'
import DeployStatusCard from './DeployStatusCard'
import WebPreviewCard from './WebPreviewCard'
import FileAttachmentCard from './FileAttachmentCard'
import ImageCard from './ImageCard'

function parseMetadata(jsonStr) {
  try {
    return JSON.parse(jsonStr)
  } catch {
    return {}
  }
}

export default function ArtifactRenderer({ block, messageId }) {
  switch (block.blockType) {
    case 'CODE':
      return <CodeBlock block={block} language={block.language} code={block.content} messageId={messageId} />

    case 'DIFF':
      return <DiffCard block={block} />

    case 'WEB_PREVIEW':
      return <WebPreviewCard block={block} />

    case 'DEPLOY_STATUS':
      return <DeployStatusCard block={block} />

    case 'FILE':
    case 'ATTACHMENT':
      return <FileAttachmentCard block={block} />

    case 'IMAGE':
    case 'IMG':
      return <ImageCard block={block} />

    case 'TEXT':
      // Plain text - render as markdown
      return (
        <div className="text-gray-300 my-2 whitespace-pre-wrap">
          {block.content}
        </div>
      )

    default:
      return (
        <div className="bg-gray-800 rounded p-3 my-2 text-gray-300 text-sm">
          <p>Unsupported artifact type: {block.blockType}</p>
          {block.content && <pre className="mt-2 text-xs overflow-auto">{block.content}</pre>}
        </div>
      )
  }
}

export { DiffCard, DeployStatusCard, WebPreviewCard, FileAttachmentCard, ImageCard, CodeBlock }
