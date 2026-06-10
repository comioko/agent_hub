import { useState, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import CodeBlock from './CodeBlock'
import ArtifactRenderer from './ArtifactCards/ArtifactRenderer'
import { useMessageStore } from '../stores/messageStore'
import { messageApi } from '../api/agenthub'
import { AgentAvatar } from './AgentStatusSidebar'

// VersionHistoryModal component
function VersionHistoryModal({ message, onClose }) {
  const [versions, setVersions] = useState([])
  const [loading, setLoading] = useState(true)
  const [selectedVersion, setSelectedVersion] = useState(null)

  useEffect(() => {
    loadVersions()
  }, [message.id])

  const loadVersions = async () => {
    try {
      const response = await messageApi.getMessageVersions(message.id)
      setVersions(response.data || [])
    } catch (error) {
      console.error('Failed to load versions:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleRestore = async (version) => {
    try {
      await messageApi.saveMessageVersion(message.id, version.content)
      alert('Version restored! Please refresh to see changes.')
      onClose()
    } catch (error) {
      console.error('Failed to restore version:', error)
      alert('Failed to restore version')
    }
  }

  const formatDate = (dateStr) => {
    return new Date(dateStr).toLocaleString()
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-gray-800 rounded-lg p-6 w-full max-w-lg mx-4 max-h-[80vh] overflow-hidden flex flex-col">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-bold text-white">Version History</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-white">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {loading ? (
          <div className="text-gray-400 text-center py-8">Loading...</div>
        ) : versions.length === 0 ? (
          <div className="text-gray-400 text-center py-8">No versions yet</div>
        ) : (
          <div className="flex-1 overflow-y-auto space-y-3">
            {/* Current version */}
            <div
              className="p-3 bg-gray-700 rounded-lg cursor-pointer border-2 border-primary-500"
              onClick={() => setSelectedVersion(null)}
            >
              <div className="flex justify-between items-start">
                <div>
                  <div className="text-white font-medium">Current Version</div>
                  <div className="text-gray-400 text-xs mt-1">{formatDate(message.createdAt)}</div>
                </div>
                <span className="text-xs text-primary-400 bg-primary-900/30 px-2 py-0.5 rounded">Current</span>
              </div>
              <div className="text-gray-300 text-sm mt-2 line-clamp-3">{message.content}</div>
            </div>

            {/* Historical versions */}
            {versions.map((version) => (
              <div
                key={version.id}
                className={`p-3 bg-gray-700 rounded-lg cursor-pointer border-2 ${
                  selectedVersion?.id === version.id ? 'border-blue-500' : 'border-transparent hover:border-gray-600'
                }`}
                onClick={() => setSelectedVersion(version)}
              >
                <div className="flex justify-between items-start">
                  <div>
                    <div className="text-white font-medium">Version {version.versionNumber}</div>
                    <div className="text-gray-400 text-xs mt-1">{formatDate(version.createdAt)}</div>
                  </div>
                  {selectedVersion?.id === version.id && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleRestore(version)
                      }}
                      className="text-xs bg-blue-600 hover:bg-blue-700 text-white px-2 py-1 rounded"
                    >
                      Restore
                    </button>
                  )}
                </div>
                <div className="text-gray-300 text-sm mt-2 line-clamp-3">{version.content}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// Highlight @mentions in text
function highlightMentions(children) {
  if (typeof children === 'string') {
    const mentionPattern = /@(\w+)/g
    const parts = children.split(mentionPattern)
    if (parts.length === 1) return children

    return parts.map((part, i) => {
      if (i % 2 === 1) {
        // This is the username part
        return <span key={i} className="text-primary-400 font-medium">@{part}</span>
      }
      return part
    })
  }
  return children
}

// ToolCallCard component for displaying tool calls
function ToolCallCard({ toolCall, result }) {
  const isPending = result === null || result === undefined
  const isSuccess = result?.success !== false
  const toolName = toolCall.tool || toolCall.name || 'unknown'
  const args = toolCall.arguments || toolCall.input || {}

  return (
    <div className={`mt-2 p-3 rounded-md border ${
      isPending ? 'border-yellow-500/50 bg-yellow-500/10' :
      isSuccess ? 'border-green-500/50 bg-green-500/10' :
      'border-red-500/50 bg-red-500/10'
    }`}>
      <div className="flex items-center gap-2 mb-1">
        <span className={`text-xs px-2 py-0.5 rounded ${
          isPending ? 'bg-yellow-500/30 text-yellow-300' :
          isSuccess ? 'bg-green-500/30 text-green-300' :
          'bg-red-500/30 text-red-300'
        }`}>
          {isPending ? 'Running' : isSuccess ? 'Success' : 'Error'}
        </span>
        <span className="text-sm font-mono text-purple-400">
          {toolName}
        </span>
      </div>

      {args && Object.keys(args).length > 0 && (
        <div className="text-xs text-gray-400 mb-2">
          <span className="font-mono">{JSON.stringify(args, null, 2)}</span>
        </div>
      )}

      {result && (
        <div className={`text-xs font-mono mt-2 p-2 rounded ${
          isSuccess ? 'bg-black/20 text-gray-300' : 'bg-red-900/30 text-red-300'
        }`}>
          <pre className="whitespace-pre-wrap break-all max-h-32 overflow-auto">
            {isSuccess ? result.result || 'OK' : result.error || 'Unknown error'}
          </pre>
        </div>
      )}
    </div>
  )
}

export default function MessageItem({ message, onPinMessage }) {
  const isUser = message.senderType === 'USER'
  const toolCalls = useMessageStore(state => state.toolCallsInProgress.get(message.id) || [])
  const [showPin, setShowPin] = useState(false)
  const [showHistory, setShowHistory] = useState(false)

  const formatTime = (dateStr) => {
    const date = new Date(dateStr)
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  return (
    <div
      className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}
      onMouseEnter={() => setShowPin(true)}
      onMouseLeave={() => setShowPin(false)}
    >
      {showHistory && (
        <VersionHistoryModal message={message} onClose={() => setShowHistory(false)} />
      )}
      <div className={`max-w-[70%] ${isUser ? 'order-2' : 'order-1'}`}>
        <div className="flex items-center gap-2 mb-1">
          {!isUser && (
            <>
              <AgentAvatar agent={{ name: message.senderName }} size="sm" showStatus={false} />
              <span className="text-sm font-medium text-primary-400">{message.senderName}</span>
            </>
          )}
          {isUser && (
            <span className="text-xs text-gray-500 ml-auto">{formatTime(message.createdAt)}</span>
          )}
          {!isUser && <span className="text-xs text-gray-500">{formatTime(message.createdAt)}</span>}
          {!isUser && (
            <button
              onClick={() => setShowHistory(true)}
              className={`p-1 rounded hover:bg-gray-600 ${showPin ? 'opacity-100' : 'opacity-0'} transition-opacity`}
              title="Version history"
            >
              <svg className="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </button>
          )}
          {!isUser && onPinMessage && (
            <button
              onClick={() => onPinMessage(message.id, !message.pinned)}
              className={`p-1 rounded hover:bg-gray-600 ${showPin ? 'opacity-100' : 'opacity-0'} transition-opacity`}
              title={message.pinned ? 'Unpin message' : 'Pin message'}
            >
              <svg
                className={`w-4 h-4 ${message.pinned ? 'text-blue-400' : 'text-gray-500'}`}
                fill={message.pinned ? 'currentColor' : 'none'}
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
              </svg>
            </button>
          )}
        </div>

        <div
          className={`rounded-lg px-4 py-3 ${
            isUser ? 'bg-primary-600 text-white' : 'bg-gray-800 text-gray-200'
          } ${isUser ? 'order-1' : 'order-2'}`}
        >
          <div className="chat-message-content">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                code({ node, inline, className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || '')
                  if (!inline && match) {
                    return (
                      <CodeBlock language={match[1]} code={String(children).replace(/\n$/, '')} />
                    )
                  }
                  return <code className={className} {...props}>{children}</code>
                },
                pre({ children }) {
                  return <>{children}</>
                },
                p({ children }) {
                  // Highlight @mentions in paragraph text
                  return <p>{highlightMentions(children)}</p>
                }
              }}
            >
              {message.content}
            </ReactMarkdown>
          </div>

          {/* Tool Calls Display */}
          {toolCalls.length > 0 && (
            <div className="mt-3 space-y-2">
              {toolCalls.map((item, idx) => (
                <ToolCallCard
                  key={item.toolCall.id || item.toolCall.callId || idx}
                  toolCall={item.toolCall}
                  result={item.result}
                />
              ))}
            </div>
          )}

          {message.blocks && message.blocks.length > 0 && (
            <div className="mt-3 space-y-2">
              {message.blocks.map((block, idx) => (
                <ArtifactRenderer key={idx} block={block} messageId={message.id} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
