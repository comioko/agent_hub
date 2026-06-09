import { useState } from 'react'

function parseMetadata(metadataStr) {
  try {
    return JSON.parse(metadataStr)
  } catch {
    return {}
  }
}

export default function DeployStatusCard({ block }) {
  const [showLogs, setShowLogs] = useState(false)
  const metadata = parseMetadata(block.metadata)

  const statusConfig = {
    DEPLOYED: {
      color: 'bg-green-500',
      text: 'text-green-400',
      label: 'Deployed'
    },
    FAILED: {
      color: 'bg-red-500',
      text: 'text-red-400',
      label: 'Failed'
    },
    BUILDING: {
      color: 'bg-yellow-500',
      text: 'text-yellow-400',
      label: 'Building'
    },
    PENDING: {
      color: 'bg-gray-500',
      text: 'text-gray-400',
      label: 'Pending'
    }
  }

  const status = metadata.status || 'PENDING'
  const config = statusConfig[status] || statusConfig.PENDING

  const formatTimestamp = (ts) => {
    if (!ts) return ''
    try {
      return new Date(ts).toLocaleString()
    } catch {
      return ts
    }
  }

  return (
    <div className="bg-gray-800 rounded-lg p-4 my-2 border border-gray-700">
      <div className="flex items-center gap-3 mb-3">
        <div className={`w-3 h-3 rounded-full ${config.color} animate-pulse`} />
        <span className={`font-medium ${config.text}`}>{config.label}</span>
        {metadata.environment && (
          <span className="text-xs text-gray-500 bg-gray-700 px-2 py-0.5 rounded">
            {metadata.environment}
          </span>
        )}
      </div>

      {metadata.deployUrl && (
        <div className="mb-2">
          <a
            href={metadata.deployUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary-400 hover:text-primary-300 text-sm flex items-center gap-1"
          >
            <span>Open deployed site</span>
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
            </svg>
          </a>
        </div>
      )}

      {metadata.commit && (
        <div className="text-xs text-gray-400 mb-2">
          Commit: <span className="font-mono">{metadata.commit.substring(0, 8)}</span>
        </div>
      )}

      {metadata.timestamp && (
        <div className="text-xs text-gray-500 mb-2">
          {formatTimestamp(metadata.timestamp)}
        </div>
      )}

      {metadata.buildLogs && (
        <details className="mt-3">
          <summary
            className="text-sm cursor-pointer text-gray-400 hover:text-gray-300"
            onClick={() => setShowLogs(!showLogs)}
          >
            {showLogs ? 'Hide' : 'Show'} Build Logs
          </summary>
          {showLogs && (
            <pre className="mt-2 p-3 bg-gray-900 rounded text-xs overflow-auto max-h-40 font-mono text-gray-300">
              {metadata.buildLogs}
            </pre>
          )}
        </details>
      )}
    </div>
  )
}
