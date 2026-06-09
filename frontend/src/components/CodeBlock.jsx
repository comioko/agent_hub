import { useState } from 'react'
import { codeBlockApi } from '../api/agenthub'

export default function CodeBlock({ block, language, code, messageId }) {
  const [copied, setCopied] = useState(false)
  const [isEditing, setIsEditing] = useState(false)
  const [editedCode, setEditedCode] = useState(code)
  const [isSaving, setIsSaving] = useState(false)
  const [isRunning, setIsRunning] = useState(false)
  const [runResult, setRunResult] = useState(null)
  const [error, setError] = useState(null)

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  const handleEdit = () => {
    setEditedCode(code)
    setIsEditing(true)
    setError(null)
    setRunResult(null)
  }

  const handleCancel = () => {
    setEditedCode(code)
    setIsEditing(false)
    setError(null)
  }

  const handleSave = async () => {
    if (!block?.id) {
      setError('Cannot save: block ID not available')
      return
    }
    setIsSaving(true)
    setError(null)
    try {
      await codeBlockApi.updateCodeBlock(block.id, editedCode)
      setIsEditing(false)
      // Trigger refresh callback if provided
      if (block.onUpdate) {
        block.onUpdate(editedCode)
      }
      window.location.reload()
    } catch (err) {
      setError(err.message || 'Failed to save')
    } finally {
      setIsSaving(false)
    }
  }

  const handleRun = async () => {
    if (!block?.id) {
      setError('Cannot run: block ID not available')
      return
    }
    setIsRunning(true)
    setError(null)
    setRunResult(null)
    try {
      const result = await codeBlockApi.executeCode(block.id)
      setRunResult(result)
    } catch (err) {
      setError(err.message || 'Failed to execute code')
    } finally {
      setIsRunning(false)
    }
  }

  return (
    <div className="relative group rounded-lg overflow-hidden bg-gray-900 my-2">
      <div className="flex items-center justify-between px-4 py-2 bg-gray-800 border-b border-gray-700">
        <span className="text-xs text-gray-400 uppercase">{language || 'code'}</span>
        <div className="flex items-center gap-2">
          {!isEditing && (
            <>
              <button
                onClick={handleRun}
                disabled={isRunning}
                className="text-gray-400 hover:text-green-400 text-xs opacity-0 group-hover:opacity-100 transition disabled:opacity-50"
              >
                {isRunning ? 'Running...' : '▶ Run'}
              </button>
              <button
                onClick={handleEdit}
                className="text-gray-400 hover:text-blue-400 text-xs opacity-0 group-hover:opacity-100 transition"
              >
                Edit
              </button>
            </>
          )}
          <button
            onClick={handleCopy}
            className="text-gray-400 hover:text-white text-xs opacity-0 group-hover:opacity-100 transition"
          >
            {copied ? 'Copied!' : 'Copy'}
          </button>
        </div>
      </div>

      {isEditing ? (
        <div className="p-4">
          <textarea
            value={editedCode}
            onChange={(e) => setEditedCode(e.target.value)}
            className="w-full min-h-[200px] bg-gray-800 text-gray-100 text-sm p-3 rounded border border-gray-700 focus:border-blue-500 focus:outline-none font-mono resize-y"
            spellCheck={false}
          />
          <div className="flex items-center justify-end gap-2 mt-3">
            <button
              onClick={handleCancel}
              className="px-3 py-1 text-xs text-gray-400 hover:text-white transition"
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={isSaving}
              className="px-3 py-1 text-xs bg-blue-600 hover:bg-blue-700 text-white rounded disabled:opacity-50 transition"
            >
              {isSaving ? 'Saving...' : 'Save'}
            </button>
          </div>
        </div>
      ) : (
        <pre className="p-4 overflow-x-auto">
          <code className={`language-${language || 'text'} text-sm text-gray-100`}>
            {code}
          </code>
        </pre>
      )}

      {(runResult || error) && !isEditing && (
        <div className="mx-4 mb-4 p-3 rounded text-sm font-mono overflow-x-auto">
          {error && (
            <div className="text-red-400 bg-red-900/30 border border-red-800 rounded p-2">
              <div className="font-semibold mb-1">Error:</div>
              <pre className="whitespace-pre-wrap">{error}</pre>
            </div>
          )}
          {runResult && (
            <div className="text-green-400 bg-green-900/30 border border-green-800 rounded p-2">
              <div className="font-semibold mb-1 text-green-300">Output:</div>
              <pre className="whitespace-pre-wrap">{runResult.output || runResult}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
