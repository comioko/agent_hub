import { useState } from 'react'
import { codeBlockApi } from '../../api/agenthub'

function parseMetadata(metadataStr) {
  try {
    return JSON.parse(metadataStr)
  } catch {
    return {}
  }
}

function DiffLine({ line }) {
  if (line.startsWith('+')) {
    return <div className="text-green-400 bg-green-400/10 px-2">{line}</div>
  }
  if (line.startsWith('-')) {
    return <div className="text-red-400 bg-red-400/10 px-2">{line}</div>
  }
  if (line.startsWith('@@')) {
    return <div className="text-blue-400 font-medium px-2">{line}</div>
  }
  return <div className="text-gray-400 px-2">{line}</div>
}

export default function DiffCard({ block }) {
  const [copied, setCopied] = useState(false)
  const [showApply, setShowApply] = useState(false)
  const [filePath, setFilePath] = useState('')
  const [isApplying, setIsApplying] = useState(false)
  const [applyResult, setApplyResult] = useState(null)
  const metadata = parseMetadata(block.metadata)
  const stats = metadata.stats || {}

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(block.content)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  const handleApply = async () => {
    if (!filePath.trim()) return
    setIsApplying(true)
    setApplyResult(null)
    try {
      const result = await codeBlockApi.applyDiff(filePath, block.content, block.content)
      setApplyResult({ success: true, message: `Applied to ${filePath}` })
      setShowApply(false)
    } catch (err) {
      setApplyResult({ success: false, message: err.message || 'Failed to apply diff' })
    } finally {
      setIsApplying(false)
    }
  }

  const lines = block.content ? block.content.split('\n') : []

  return (
    <div className="rounded-lg overflow-hidden bg-gray-900 my-2">
      <div className="flex items-center justify-between px-4 py-2 bg-gray-800 border-b border-gray-700">
        <div className="flex items-center gap-4">
          <span className="text-xs text-gray-400 uppercase">
            {block.language || 'diff'}
          </span>
          <div className="flex items-center gap-3 text-xs">
            <span className="text-red-400">-{stats.deletions || 0}</span>
            <span className="text-green-400">+{stats.additions || 0}</span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowApply(!showApply)}
            className="text-gray-400 hover:text-blue-400 text-xs px-2 py-1 rounded hover:bg-gray-700"
          >
            {showApply ? 'Cancel' : 'Apply'}
          </button>
          <button
            onClick={handleCopy}
            className="text-gray-400 hover:text-white text-xs"
          >
            {copied ? 'Copied!' : 'Copy'}
          </button>
        </div>
      </div>

      {showApply && (
        <div className="px-4 py-3 bg-gray-800 border-b border-gray-700">
          <div className="flex items-center gap-2 mb-2">
            <input
              type="text"
              value={filePath}
              onChange={(e) => setFilePath(e.target.value)}
              placeholder="Enter file path (e.g., src/App.jsx)"
              className="flex-1 px-3 py-2 bg-gray-700 border border-gray-600 rounded text-white text-sm placeholder-gray-400 focus:outline-none focus:border-blue-500"
            />
            <button
              onClick={handleApply}
              disabled={!filePath.trim() || isApplying}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm rounded disabled:opacity-50 transition"
            >
              {isApplying ? 'Applying...' : 'Apply'}
            </button>
          </div>
          {applyResult && (
            <div className={`text-xs ${applyResult.success ? 'text-green-400' : 'text-red-400'}`}>
              {applyResult.message}
            </div>
          )}
        </div>
      )}

      <pre className="p-2 overflow-x-auto text-sm font-mono max-h-80 overflow-y-auto">
        {lines.map((line, i) => (
          <DiffLine key={i} line={line} />
        ))}
      </pre>
    </div>
  )
}
