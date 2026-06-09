import { useState } from 'react'

function parseMetadata(metadataStr) {
  try {
    return JSON.parse(metadataStr)
  } catch {
    return {}
  }
}

export default function WebPreviewCard({ block }) {
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState(false)
  const metadata = parseMetadata(block.metadata)

  if (!metadata.url) {
    return null
  }

  return (
    <div className="bg-gray-800 rounded-lg overflow-hidden my-2 border border-gray-700">
      <div className="flex items-center gap-3 p-3 bg-gray-700/50">
        {metadata.favicon && (
          <img
            src={metadata.favicon}
            alt=""
            className="w-4 h-4"
            onError={(e) => { e.target.style.display = 'none' }}
          />
        )}
        <div className="flex-1 min-w-0">
          <a
            href={metadata.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-sm font-medium text-white hover:text-primary-300 truncate block"
          >
            {metadata.title || metadata.url}
          </a>
          {metadata.description && (
            <p className="text-xs text-gray-400 truncate mt-0.5">
              {metadata.description}
            </p>
          )}
        </div>
        <a
          href={metadata.url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-gray-400 hover:text-white"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
          </svg>
        </a>
      </div>
    </div>
  )
}
