import CodeBlock from '../CodeBlock'
import DiffCard from './DiffCard'
import DeployStatusCard from './DeployStatusCard'
import WebPreviewCard from './WebPreviewCard'

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

    default:
      return (
        <div className="bg-gray-800 rounded p-3 my-2 text-gray-300 text-sm">
          <p>Unsupported artifact type: {block.blockType}</p>
          {block.content && <pre className="mt-2 text-xs overflow-auto">{block.content}</pre>}
        </div>
      )
  }
}

export { DiffCard, DeployStatusCard, WebPreviewCard, CodeBlock }
